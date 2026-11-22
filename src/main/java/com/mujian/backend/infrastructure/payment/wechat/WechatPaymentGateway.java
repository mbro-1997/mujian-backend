package com.mujian.backend.infrastructure.payment.wechat;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.enums.ProfitSharingOutcome;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.order.policy.TradingTime;
import com.mujian.backend.payment.dto.PaymentAccount;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;

/** 官方平台收付通协议；资金写入不自动重试，错误不解释为未受理。 */
@Component
public class WechatPaymentGateway implements PaymentGateway, MerchantGateway, ProfitSharingGateway {
  private record Reply(int status, JsonNode body) {}

  private final Environment environment;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final WechatPaymentCrypto wechatPaymentCrypto;
  private final URI endpoint;
  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  /** 运行环境固定微信HTTPS主机，不允许前端控制请求目标。 */
  @org.springframework.beans.factory.annotation.Autowired
  public WechatPaymentGateway(
      Environment environment,
      ObjectMapper objectMapper,
      Clock clock,
      WechatPaymentCrypto wechatPaymentCrypto) {
    this(
        environment,
        objectMapper,
        clock,
        wechatPaymentCrypto,
        URI.create("https://api.mch.weixin.qq.com"));
  }

  /** 包内隔离协议测试使用本地服务器，不暴露运行配置切换主机能力。 */
  WechatPaymentGateway(
      Environment environment,
      ObjectMapper objectMapper,
      Clock clock,
      WechatPaymentCrypto wechatPaymentCrypto,
      URI endpoint) {
    this.environment = environment;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.wechatPaymentCrypto = wechatPaymentCrypto;
    this.endpoint = endpoint;
  }

  /** 支付缺配置时明示不可用，其他模块仍可启动。 */
  public PaymentAccount requireReady() {
    String app = environment.getProperty("mj.payment.wechat.app-id", ""),
        mch = environment.getProperty("mj.payment.wechat.mch-id", ""),
        url = environment.getProperty("mj.payment.wechat.notify-url", "");
    try {
      URI uri = URI.create(url);
      if (!app.matches("[A-Za-z0-9_-]{1,64}")
          || !mch.matches("[0-9]{1,32}")
          || !"https".equals(uri.getScheme())
          || uri.getHost() == null
          || uri.getQuery() != null
          || uri.getFragment() != null
          || !"/api/wechat-pay/notifications/payment".equals(uri.getPath()))
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException exception) {
      throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    }
    wechatPaymentCrypto.requireReady();
    return new PaymentAccount(app, mch, url);
  }

  /** 一至五十子单冻结待分账，固定原到期秒，不加服务费。 */
  public String create(Create create) {
    if (create.suborders().isEmpty() || create.suborders().size() > 50)
      throw new IllegalArgumentException("invalid suborders");
    if (!TradingTime.canCreatePayment(create.expiry(), now(clock)))
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    ObjectNode body = objectMapper.createObjectNode();
    body.put("combine_appid", create.account().appId());
    body.put("combine_mchid", create.account().mchId());
    body.put("combine_out_trade_no", create.outTradeNo());
    body.put(
        "time_expire",
        create
            .expiry()
            .withNano(0)
            .atOffset(ZoneOffset.ofHours(8))
            .format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX")));
    body.put("notify_url", create.account().notifyUrl());
    body.putObject("combine_payer_info").put("openid", create.openid());
    ArrayNode suborders = body.putArray("sub_orders");
    for (Suborder suborder : create.suborders()) {
      ObjectNode item = suborders.addObject();
      item.put("mchid", create.account().mchId());
      item.put("sub_mchid", suborder.subMchid());
      item.put("out_trade_no", suborder.outTradeNo());
      item.put("description", suborder.description());
      item.putObject("amount").put("total_amount", suborder.totalFen()).put("currency", "CNY");
      item.putObject("settle_info").put("profit_sharing", true);
    }
    Reply reply = exchange(create.account(), "POST", "/v3/combine-transactions/jsapi", body);
    if (reply.status() != 200) throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    return string(reply.body(), "prepay_id", 128);
  }

  /** 查单返回已验签全部子单；不存在只是一条事实，由业务结合原尝试记录处理。 */
  public PaymentFact query(Reference reference) {
    Reply reply =
        exchange(
            reference.account(),
            "GET",
            "/v3/combine-transactions/out-trade-no/" + segment(reference.outTradeNo()),
            null);
    if (reply.status() == 404
        && Set.of("ORDERNOTEXIST", "ORDER_NOT_EXIST")
            .contains(reply.body().path("code").asString()))
      return new PaymentFact(
          reference.account().appId(),
          reference.account().mchId(),
          reference.outTradeNo(),
          List.of(),
          true);
    if (reply.status() != 200) throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    return paymentFact(reply.body());
  }

  /** 关单仅识别官方明确状态码/错误码；验签失败、其他404及超时均保留未知。 */
  public PaymentCloseResult close(Reference reference) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("combine_appid", reference.account().appId());
    ArrayNode suborders = body.putArray("sub_orders");
    for (Suborder suborder : reference.suborders())
      suborders
          .addObject()
          .put("mchid", reference.account().mchId())
          .put("sub_mchid", suborder.subMchid())
          .put("out_trade_no", suborder.outTradeNo());
    Reply reply =
        exchange(
            reference.account(),
            "POST",
            "/v3/combine-transactions/out-trade-no/" + segment(reference.outTradeNo()) + "/close",
            body);
    if (reply.status() == 204) return PaymentCloseResult.accepted;
    if (reply.status() == 404
        && Set.of("ORDERNOTEXIST", "ORDER_NOT_EXIST")
            .contains(reply.body().path("code").asString())) return PaymentCloseResult.not_found;
    if (reply.status() == 400 && "ORDER_CLOSED".equals(reply.body().path("code").asString()))
      return PaymentCloseResult.already_closed;
    throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
  }

  /** 原二级商户原路退款，资金来源使用渠道默认原交易账户。 */
  public RefundFact refund(RefundRequest refundRequest) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("sub_mchid", refundRequest.subMchid());
    body.put("sp_appid", refundRequest.account().appId());
    body.put("out_refund_no", refundRequest.refundNo());
    String reason = refundRequest.reason();
    if (reason != null && reason.codePointCount(0, reason.length()) > 80)
      reason = reason.substring(0, reason.offsetByCodePoints(0, 80));
    body.put("reason", reason);
    body.put("notify_url", refundRequest.notifyUrl());
    body.put("refund_account", "REFUND_SOURCE_SUB_MERCHANT");
    if (refundRequest.transactionId() != null)
      body.put("transaction_id", refundRequest.transactionId());
    else body.put("out_trade_no", refundRequest.outTradeNo());
    body.putObject("amount")
        .put("refund", refundRequest.refundFen())
        .put("total", refundRequest.totalFen())
        .put("currency", "CNY");
    Reply reply = exchange(refundRequest.account(), "POST", "/v3/ecommerce/refunds/apply", body);
    if (reply.status() != 200) throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    return refundFact(
        reply.body(), false, refundRequest.account().mchId(), refundRequest.subMchid());
  }

  /** 超时、500和迟通知均查询同一退款号，不生成新的退款号。 */
  public RefundFact queryRefund(RefundRequest refundRequest) {
    Reply reply =
        exchange(
            refundRequest.account(),
            "GET",
            "/v3/ecommerce/refunds/out-refund-no/"
                + segment(refundRequest.refundNo())
                + "?sub_mchid="
                + segment(refundRequest.subMchid()),
            null);
    if (reply.status() == 404
        && Set.of("RESOURCE_NOT_EXISTS", "RESOURCE_NOT_EXIST", "REFUND_NOT_EXIST")
            .contains(reply.body().path("code").asString()))
      return new RefundFact(
          refundRequest.account().mchId(),
          refundRequest.subMchid(),
          refundRequest.outTradeNo(),
          refundRequest.transactionId(),
          refundRequest.refundNo(),
          null,
          null,
          null,
          0,
          null,
          null,
          true);
    if (reply.status() != 200) throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    return refundFact(
        reply.body(), false, refundRequest.account().mchId(), refundRequest.subMchid());
  }

  /** 微信调起签名不写前端持久缓存，也不表示支付成功。 */
  public Parameters parameters(String appId, String prepayId) {
    String timestamp = Long.toString(clock.instant().getEpochSecond()),
        nonce = Ids.next(),
        value = "prepay_id=" + prepayId;
    return new Parameters(
        appId,
        timestamp,
        nonce,
        value,
        "RSA",
        wechatPaymentCrypto.sign(appId + "\n" + timestamp + "\n" + nonce + "\n" + value + "\n"));
  }

  /** 原始字节先验签，再验证通知类型并解密；调用方在持久化成功后才返回204。 */
  public Notification notification(String kind, Headers headers, byte[] body) {
    if (body.length > 1048576) throw ApiFailure.invalid();
    wechatPaymentCrypto.verify(headers, body);
    return restoreVerifiedNotification(
        kind, headers.serial(), new String(body, StandardCharsets.UTF_8));
  }

  /** 入箱前已验签，重启后仅解密已持久可信原文，不受通知签名五分钟窗口影响。 */
  public Notification restoreVerifiedNotification(
      String kind, String serial, String encryptedJson) {
    if (!Set.of("payment", "refund").contains(kind) || encryptedJson.length() > 1048576)
      throw ApiFailure.invalid();
    JsonNode json = objectMapper.readTree(encryptedJson);
    String eventId = string(json, "id", 64), eventType = string(json, "event_type", 64);
    JsonNode resource = json.path("resource");
    if (!"AEAD_AES_256_GCM".equals(resource.path("algorithm").asString())
        || !"encrypt-resource".equals(json.path("resource_type").asString()))
      throw ApiFailure.invalid();
    if ("payment".equals(kind) && !"TRANSACTION.SUCCESS".equals(eventType)
        || "refund".equals(kind)
            && !Set.of("REFUND.SUCCESS", "REFUND.CLOSED", "REFUND.ABNORMAL").contains(eventType))
      throw ApiFailure.invalid();
    JsonNode decrypted =
        objectMapper.readTree(
            wechatPaymentCrypto.decrypt(
                string(resource, "nonce", 32),
                resource.path("associated_data").asString(""),
                string(resource, "ciphertext", 1048576)));
    PaymentFact paymentFactPayment = "payment".equals(kind) ? paymentFact(decrypted) : null;
    RefundFact refundFactRefund =
        "refund".equals(kind) ? refundFact(decrypted, true, null, null) : null;
    if (paymentFactPayment != null
            && paymentFactPayment.suborders().stream()
                .anyMatch(suborderFactItem -> !"SUCCESS".equals(suborderFactItem.state()))
        || refundFactRefund != null && !eventType.equals("REFUND." + refundFactRefund.state()))
      throw ApiFailure.invalid();
    return new Notification(
        eventId, eventType, serial, encryptedJson, paymentFactPayment, refundFactRefund);
  }

  /** 平台收付通进件查询只使用已有可信编号；FINISH不推断分账权限。 */
  public MerchantGateway.Status query(String applymentId, String requestNo) {
    if (applymentId == null && requestNo == null)
      throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    String path =
        applymentId != null
            ? "/v3/ecommerce/applyments/" + segment(applymentId)
            : "/v3/ecommerce/applyments/out-request-no/" + segment(requestNo);
    Reply reply = exchange(requireReady(), "GET", path, null);
    if (reply.status() != 200) throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    JsonNode body = reply.body(), id = body.path("applyment_id");
    if (!id.isIntegralNumber() || !id.toString().matches("[1-9][0-9]{0,63}"))
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    String originalRequest = string(body, "out_request_no", 64),
        state = string(body, "applyment_state", 32),
        sub = body.path("sub_mchid").asString(null);
    if (applymentId != null && !applymentId.equals(id.toString())
        || requestNo != null && !requestNo.equals(originalRequest))
      throw conflict(TradingReason.CHANNEL_IDENTITY_MISMATCH.name());
    if (!Set.of(
            "CHECKING",
            "ACCOUNT_NEED_VERIFY",
            "AUDITING",
            "REJECTED",
            "NEED_SIGN",
            "FINISH",
            "FROZEN",
            "CANCELED")
        .contains(state)) throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    String action =
        "NEED_SIGN".equals(state)
            ? body.path("sign_url").asString(null)
            : "ACCOUNT_NEED_VERIFY".equals(state)
                ? body.path("legal_validation_url").asString(null)
                : null;
    if (action != null) {
      try {
        URI uri = URI.create(action);
        if (action.length() > 2048
            || !"https".equals(uri.getScheme())
            || !"pay.weixin.qq.com".equals(uri.getHost())
            || uri.getUserInfo() != null
            || uri.getPort() != -1) throw new IllegalArgumentException();
      } catch (IllegalArgumentException exception) {
        throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
      }
    }
    if (sub != null && !sub.matches("[0-9]{1,32}")
        || Set.of("FINISH", "NEED_SIGN").contains(state) && sub == null)
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    return new MerchantGateway.Status(id.toString(), originalRequest, sub, state, action);
  }

  /** 分佣与解冻分别按持久原号查询，创建应答只表示受理。 */
  @Override
  public Observation execute(ProfitSharingGateway.Request request, boolean query) {
    PaymentAccount account = requireReady();
    if (!account.mchId().equals(request.platformMerchant()))
      return new Observation(ProfitSharingOutcome.MISMATCH, 0, null);
    String path = "/v3/ecommerce/profitsharing/orders";
    ObjectNode input = null;
    if (query) {
      path +=
          "?sub_mchid="
              + segment(request.subMerchant())
              + "&transaction_id="
              + segment(request.transactionId())
              + "&out_order_no="
              + segment(request.outNo());
    } else {
      input =
          objectMapper
              .createObjectNode()
              .put("sub_mchid", request.subMerchant())
              .put("transaction_id", request.transactionId())
              .put("out_order_no", request.outNo());
      if (request.finish()) {
        path = "/v3/ecommerce/profitsharing/finish-order";
        input.put("description", "租赁订单剩余资金解冻");
      } else {
        input.put("appid", account.appId()).put("finish", false);
        input
            .putArray("receivers")
            .addObject()
            .put("type", "MERCHANT_ID")
            .put("receiver_account", request.platformMerchant())
            .put("amount", request.amount())
            .put("description", "租赁订单平台佣金");
      }
    }
    Reply reply = exchange(account, query ? "GET" : "POST", path, input);
    JsonNode data = reply.body();
    String code = data.path("code").asString("");
    if (query && reply.status() == 404 && code.equals("RESOURCE_NOT_EXISTS"))
      return new Observation(ProfitSharingOutcome.ABSENT, 0, null);
    if (!query && reply.status() == 429 && "FREQUENCY_LIMITED".equals(code))
      return new Observation(ProfitSharingOutcome.RETRYABLE, 0, null);
    if (reply.status() != 200) return new Observation(ProfitSharingOutcome.UNKNOWN, 0, null);
    if (!request.subMerchant().equals(data.path("sub_mchid").asString(""))
        || !request.transactionId().equals(data.path("transaction_id").asString(""))
        || !request.outNo().equals(data.path("out_order_no").asString("")))
      return new Observation(ProfitSharingOutcome.MISMATCH, 0, null);
    String channelNo = string(data, "order_id", 64);
    if (!query) return new Observation(ProfitSharingOutcome.ACCEPTED, 0, channelNo);
    String status = data.path("status").asString("");
    if ("PROCESSING".equals(status)) return new Observation(ProfitSharingOutcome.PENDING, 0, channelNo);
    if (!"FINISHED".equals(status)) return new Observation(ProfitSharingOutcome.UNKNOWN, 0, channelNo);
    int actual;
    if (request.finish()) {
      actual = integer(data, "finish_amount");
    } else {
      JsonNode rows = data.path("receivers");
      if (!rows.isArray() || rows.size() != 1)
        return new Observation(ProfitSharingOutcome.MISMATCH, 0, channelNo);
      JsonNode row = rows.get(0);
      if (!"MERCHANT_ID".equals(row.path("type").asString(""))
          || !request.platformMerchant().equals(row.path("receiver_account").asString("")))
        return new Observation(ProfitSharingOutcome.MISMATCH, 0, channelNo);
      String result = row.path("result").asString("");
      if ("CLOSED".equals(result))
        return new Observation(ProfitSharingOutcome.CLOSED, 0, channelNo,
            WechatSharingCloseReason.fromChannel(row.path("fail_reason").asString("")));
      if ("PENDING".equals(result)) return new Observation(ProfitSharingOutcome.PENDING, 0, channelNo);
      if (!"SUCCESS".equals(result)) return new Observation(ProfitSharingOutcome.UNKNOWN, 0, channelNo);
      actual = integer(row, "amount");
    }
    return new Observation(
        actual == request.amount() ? ProfitSharingOutcome.SUCCESS : ProfitSharingOutcome.MISMATCH,
        actual,
        channelNo);
  }

  /** 单次有限超时的请求，先验响应签名再读取交易字段；不带敏感正文到异常。 */
  private Reply exchange(
      PaymentAccount paymentAccount, String method, String path, ObjectNode body) {
    String payload = body == null ? "" : objectMapper.writeValueAsString(body),
        timestamp = Long.toString(clock.instant().getEpochSecond()),
        nonce = Ids.next();
    String signature =
        wechatPaymentCrypto.sign(
            method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + payload + "\n");
    String authorization =
        "WECHATPAY2-SHA256-RSA2048 mchid=\""
            + paymentAccount.mchId()
            + "\",nonce_str=\""
            + nonce
            + "\",timestamp=\""
            + timestamp
            + "\",serial_no=\""
            + wechatPaymentCrypto.merchantSerial()
            + "\",signature=\""
            + signature
            + "\"";
    HttpRequest request =
        HttpRequest.newBuilder(endpoint.resolve(path))
            .timeout(Duration.ofSeconds(5))
            .header("Authorization", authorization)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();
    try {
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.body().length > 1048576)
        throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
      HttpHeaders headers = response.headers();
      wechatPaymentCrypto.verify(
          new Headers(
              headers.firstValue("Wechatpay-Timestamp").orElse(null),
              headers.firstValue("Wechatpay-Nonce").orElse(null),
              headers.firstValue("Wechatpay-Serial").orElse(null),
              headers.firstValue("Wechatpay-Signature").orElse(null)),
          response.body());
      return new Reply(
          response.statusCode(),
          response.body().length == 0
              ? objectMapper.createObjectNode()
              : objectMapper.readTree(response.body()));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    } catch (java.io.IOException | tools.jackson.core.JacksonException exception) {
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    }
  }

  /** 支付通知和主动查询共用白名单，逐子单保留商户和币种以供资金层核验。 */
  private PaymentFact paymentFact(JsonNode body) {
    JsonNode suborders = body.path("sub_orders");
    if (!suborders.isArray() || suborders.isEmpty() || suborders.size() > 50)
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    List<SuborderFact> suborderFactListFacts = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JsonNode item : suborders) {
      String id = string(item, "out_trade_no", 64);
      if (!ids.add(id)) throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
      JsonNode amount = item.path("amount");
      String state = string(item, "trade_state", 32);
      suborderFactListFacts.add(
          new SuborderFact(
              string(item, "mchid", 64),
              string(item, "sub_mchid", 64),
              id,
              item.path("transaction_id").asString(null),
              state,
              integer(amount, "total_amount"),
              string(amount, "currency", 8),
              amount.hasNonNull("payer_amount") ? integer(amount, "payer_amount") : null,
              amount.path("payer_currency").asString(null),
              instant(item, "success_time")));
    }
    return new PaymentFact(
        string(body, "combine_appid", 64),
        string(body, "combine_mchid", 64),
        string(body, "combine_out_trade_no", 64),
        List.copyOf(suborderFactListFacts),
        false);
  }

  /** 官方退款通知没有AppID/币种字段，必须再由原支付来源核对；显式异常币种仍拒绝。 */
  private RefundFact refundFact(JsonNode body, boolean notify, String mchid, String subMchid) {
    JsonNode amount = body.path("amount");
    String currency = amount.path("currency").asString(null);
    return new RefundFact(
        notify ? string(body, "sp_mchid", 64) : mchid,
        notify ? string(body, "sub_mchid", 64) : subMchid,
        string(body, "out_trade_no", 64),
        string(body, "transaction_id", 64),
        string(body, "out_refund_no", 64),
        string(body, "refund_id", 64),
        string(body, notify ? "refund_status" : "status", 32),
        amount.hasNonNull("total") ? integer(amount, "total") : null,
        integer(amount, "refund"),
        currency,
        instant(body, "success_time"),
        false);
  }

  /** 渠道标识只能作为单一路径段，避免原字段改变请求目标。 */
  private static String segment(String value) {
    if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}"))
      throw new IllegalArgumentException("invalid channel identifier");
    return value;
  }

  /** 只接受有界非空字符串，不隐式将对象或数字转换为标识。 */
  private static String string(JsonNode node, String field, int max) {
    JsonNode value = node.get(field);
    if (value == null
        || !value.isString()
        || value.asString().isEmpty()
        || value.asString().length() > max)
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    return value.asString();
  }

  /** 金额只接受整数分和受支持范围。 */
  private static int integer(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0)
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    return value.asInt();
  }

  /** 渠道成功时间保留真实时刻，不用接收时间替代。 */
  private static Instant instant(JsonNode node, String field) {
    if (!node.hasNonNull(field)) return null;
    try {
      return OffsetDateTime.parse(string(node, field, 64)).toInstant();
    } catch (DateTimeParseException exception) {
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    }
  }
}
