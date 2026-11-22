package com.mujian.backend.infrastructure.payment.wechat;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.ProfitSharingOutcome;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;

/** 官方收付通请求及响应验签回归。 */
class WechatPaymentGatewayTest extends SignedWechatHttpFixture {
  /** 校验分账使用原收款人及经确认的完结金额。 */
  @Test
  void profitSharingChecksOriginalRecipientAndVerifiedFinishAmount() throws Exception {
    String transaction = "420000000000000000000000", sub = "190000002";
    ProfitSharingGateway.Request share =
        new ProfitSharingGateway.Request(
            account.mchId(), sub, transaction, "a".repeat(32), false, 201);
    ProfitSharingGateway.Request finish =
        new ProfitSharingGateway.Request(
            account.mchId(), sub, transaction, "b".repeat(32), true, 804);
    ObjectNode data =
        json.createObjectNode()
            .put("sub_mchid", sub)
            .put("transaction_id", transaction)
            .put("out_order_no", share.outNo())
            .put("order_id", "wx-share")
            .put("status", "FINISHED");
    ObjectNode recipient =
        data.putArray("receivers")
            .addObject()
            .put("type", "MERCHANT_ID")
            .put("receiver_account", account.mchId())
            .put("result", "SUCCESS")
            .put("amount", 201);
    reply.set(data.toString());
    assertEquals(ProfitSharingOutcome.ACCEPTED, gateway.execute(share, false).outcome());
    assertFalse(json.readTree(lastBody.get()).path("finish").asBoolean());
    assertEquals(ProfitSharingOutcome.SUCCESS, gateway.execute(share, true).outcome());
    recipient.put("result", "CLOSED").put("fail_reason", "NO_RELATION");
    reply.set(data.toString());
    ProfitSharingGateway.Observation closed = gateway.execute(share, true);
    assertEquals(ProfitSharingOutcome.CLOSED, closed.outcome());
    assertEquals("NO_RELATION", closed.closeReason().name());
    recipient.put("fail_reason", "unexpected private message");
    reply.set(data.toString());
    assertEquals("CHANNEL_CLOSED", gateway.execute(share, true).closeReason().name());
    recipient.put("result", "PENDING");
    reply.set(data.toString());
    assertEquals(ProfitSharingOutcome.PENDING, gateway.execute(share, true).outcome());
    recipient.put("result", "unrecognized");
    reply.set(data.toString());
    assertEquals(ProfitSharingOutcome.UNKNOWN, gateway.execute(share, true).outcome());
    replyStatus = 429;
    reply.set("{\"code\":\"FREQUENCY_LIMITED\"}");
    assertEquals(ProfitSharingOutcome.RETRYABLE, gateway.execute(share, false).outcome());
    assertEquals(ProfitSharingOutcome.UNKNOWN, gateway.execute(share, true).outcome());
    replyStatus = 200;
    recipient.put("receiver_account", sub);
    reply.set(data.toString());
    assertEquals(ProfitSharingOutcome.MISMATCH, gateway.execute(share, true).outcome());
    data.remove("receivers");
    data.put("out_order_no", finish.outNo()).put("order_id", "wx-finish").put("finish_amount", 803);
    reply.set(data.toString());
    assertEquals(ProfitSharingOutcome.ACCEPTED, gateway.execute(finish, false).outcome());
    assertEquals("/v3/ecommerce/profitsharing/finish-order", lastPath.get());
    assertFalse(json.readTree(lastBody.get()).has("amount"));
    assertEquals(ProfitSharingOutcome.MISMATCH, gateway.execute(finish, true).outcome());
    data.put("finish_amount", 804);
    reply.set(data.toString());
    assertEquals(804, gateway.execute(finish, true).actual());
    badSignature = true;
    assertThrows(ApiFailure.class, () -> gateway.execute(finish, true));
  }

  /** 使用真实 RSA 验证 HTTP 协议和退款来源。 */
  @Test
  void realRsaHttpContractsAndRefundSource() throws Exception {
    PaymentGateway.Create request =
        new PaymentGateway.Create(
            account,
            "combine-original",
            "openid",
            LocalDateTime.of(2026, 11, 7, 17, 10),
            List.of(new PaymentGateway.Suborder("sub-original", "190000002", "幕间道具租赁", 500)));
    assertEquals("isolated-prepay", gateway.create(request));
    JsonNode body = json.readTree(lastBody.get());
    assertEquals("/v3/combine-transactions/jsapi", lastPath.get());
    assertTrue(
        body.path("sub_orders").get(0).path("settle_info").path("profit_sharing").asBoolean());
    assertEquals("2026-11-07T17:10:00+08:00", body.path("time_expire").asString());
    PaymentGateway.Parameters parameters = gateway.parameters(account.appId(), "isolated-prepay");
    Signature verify = Signature.getInstance("SHA256withRSA");
    verify.initVerify(merchant.getPublic());
    verify.update(
        (parameters.appId()
                + "\n"
                + parameters.timeStamp()
                + "\n"
                + parameters.nonceStr()
                + "\n"
                + parameters.packageValue()
                + "\n")
            .getBytes(StandardCharsets.UTF_8));
    assertTrue(verify.verify(Base64.getDecoder().decode(parameters.paySign())));
    badSignature = true;
    assertThrows(ApiFailure.class, () -> gateway.create(request));
    badSignature = false;
    reply.set(
        "{\"refund_id\":\"wx-refund\",\"out_refund_no\":\"refund-original\",\"out_trade_no\":\"sub-original\",\"transaction_id\":\"wx-sub\",\"status\":\"PROCESSING\",\"amount\":{\"refund\":200,\"currency\":\"CNY\"}}");
    PaymentGateway.RefundRequest refund =
        new PaymentGateway.RefundRequest(
            account,
            "190000002",
            "sub-original",
            "wx-sub",
            "refund-original",
            500,
            200,
            "取消租赁".repeat(30),
            "https://example.invalid/api/wechat-pay/notifications/refund");
    assertEquals("PROCESSING", gateway.refund(refund).state());
    body = json.readTree(lastBody.get());
    assertEquals(
        80,
        body.path("reason").asString().codePointCount(0, body.path("reason").asString().length()));
    assertEquals("/v3/ecommerce/refunds/apply", lastPath.get());
    assertEquals("REFUND_SOURCE_SUB_MERCHANT", body.path("refund_account").asString());
    assertFalse(body.has("funds_account"));
    assertFalse(body.path("amount").has("from"));
    assertFalse(body.has("out_trade_no"));
    assertEquals("wx-sub", body.path("transaction_id").asString());
    gateway.queryRefund(refund);
    assertEquals(
        "/v3/ecommerce/refunds/out-refund-no/refund-original?sub_mchid=190000002", lastPath.get());
  }

  /** 本地协议服务器验证精确状态码和验签，不把任意404或失败等价为不存在。 */
  @Test
  void closeAndQueryRecognizeOnlyVerifiedOfficialAbsence() {
    PaymentGateway.Reference reference =
        new PaymentGateway.Reference(
            account,
            "combine-original",
            List.of(new PaymentGateway.Suborder("sub-original", "190000002", "隔离订单", 500)));
    for (String code : List.of("ORDERNOTEXIST", "ORDER_NOT_EXIST")) {
      replyStatus = 404;
      reply.set("{\"code\":\"" + code + "\",\"message\":\"订单不存在\"}");
      assertTrue(gateway.query(reference).notFound());
      assertEquals(
          com.mujian.backend.common.enums.PaymentCloseResult.not_found, gateway.close(reference));
      assertEquals("/v3/combine-transactions/out-trade-no/combine-original/close", lastPath.get());
      JsonNode body = json.readTree(lastBody.get());
      assertEquals(account.appId(), body.path("combine_appid").asString());
      assertEquals("sub-original", body.path("sub_orders").get(0).path("out_trade_no").asString());
      badSignature = true;
      assertThrows(ApiFailure.class, () -> gateway.query(reference));
      assertThrows(ApiFailure.class, () -> gateway.close(reference));
      badSignature = false;
    }
    reply.set("{\"code\":\"RESOURCE_NOT_EXISTS\"}");
    assertThrows(ApiFailure.class, () -> gateway.query(reference));
    assertThrows(ApiFailure.class, () -> gateway.close(reference));
    replyStatus = 500;
    reply.set("{\"code\":\"ORDERNOTEXIST\"}");
    assertThrows(ApiFailure.class, () -> gateway.query(reference));
    assertThrows(ApiFailure.class, () -> gateway.close(reference));
    replyStatus = 400;
    reply.set("{\"code\":\"ORDER_CLOSED\"}");
    assertEquals(
        com.mujian.backend.common.enums.PaymentCloseResult.already_closed,
        gateway.close(reference));
    replyStatus = 204;
    reply.set("");
    assertEquals(
        com.mujian.backend.common.enums.PaymentCloseResult.accepted, gateway.close(reference));
  }

  /** 验证通知先校验原始签名字节再解密。 */
  @Test
  void notificationVerifiesOriginalBytesBeforeDecrypting() throws Exception {
    String plaintext =
        "{\"combine_appid\":\"wx-test\",\"combine_mchid\":\"190000001\",\"combine_out_trade_no\":\"combine-original\",\"sub_orders\":[{\"mchid\":\"190000001\",\"sub_mchid\":\"190000002\",\"out_trade_no\":\"sub-original\",\"transaction_id\":\"wx-sub\",\"trade_state\":\"SUCCESS\",\"success_time\":\"2026-11-07T17:00:00+08:00\",\"amount\":{\"total_amount\":500,\"payer_amount\":500,\"currency\":\"CNY\",\"payer_currency\":\"CNY\"}}]}";
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.ENCRYPT_MODE,
        new SecretKeySpec(
            environment
                .getProperty("mj.payment.wechat.api-v3-key")
                .getBytes(StandardCharsets.UTF_8),
            "AES"),
        new GCMParameterSpec(128, "123456789012".getBytes(StandardCharsets.UTF_8)));
    cipher.updateAAD("transaction".getBytes(StandardCharsets.UTF_8));
    ObjectNode body =
        json.createObjectNode()
            .put("id", "event-original")
            .put("event_type", "TRANSACTION.SUCCESS")
            .put("resource_type", "encrypt-resource");
    body.putObject("resource")
        .put("algorithm", "AEAD_AES_256_GCM")
        .put("nonce", "123456789012")
        .put("associated_data", "transaction")
        .put(
            "ciphertext",
            Base64.getEncoder()
                .encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8))));
    String raw = json.writeValueAsString(body),
        timestamp = Long.toString(clock.instant().getEpochSecond());
    PaymentGateway.Headers headers =
        new PaymentGateway.Headers(
            timestamp,
            "header-nonce",
            "PUB_KEY_ID_TEST",
            sign(platform.getPrivate(), timestamp + "\nheader-nonce\n" + raw + "\n"));
    PaymentGateway.Notification notification =
        gateway.notification("payment", headers, raw.getBytes(StandardCharsets.UTF_8));
    assertEquals(500, notification.payment().suborders().get(0).totalFen());
    assertFalse(notification.encryptedJson().contains("sub-original"));
    assertThrows(
        ApiFailure.class,
        () ->
            gateway.notification("payment", headers, (raw + " ").getBytes(StandardCharsets.UTF_8)));
    assertThrows(
        ApiFailure.class,
        () -> gateway.notification("refund", headers, raw.getBytes(StandardCharsets.UTF_8)));
  }
}
