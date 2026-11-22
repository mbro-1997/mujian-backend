package com.mujian.backend.common.security;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.D2Contract.Route;
import com.mujian.backend.common.support.Ids;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(-200)
public class ApiBoundaryFilter extends OncePerRequestFilter {
  private static final Map<String, String> ROUTES =
      Map.of(
          "/api/admin/auth/csrf",
          "GET",
          "/api/admin/auth/login",
          "POST",
          "/api/admin/me",
          "GET",
          "/api/admin/menus",
          "GET",
          "/api/admin/system/menus",
          "GET",
          "/api/admin/auth/logout",
          "POST",
          "/api/me/context",
          "GET",
          "/api/auth/logout",
          "POST");
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ApiBoundaryFilter.class);
  private final SessionService sessionService;
  private final ObjectMapper json;
  private final D2Contract d2;

  @org.springframework.beans.factory.annotation.Value("${mj.planning.full-save-max-bytes:1048576}")
  private int fullSaveMaxBytes;

  public ApiBoundaryFilter(SessionService s, ObjectMapper j, D2Contract d2Contract) {
    sessionService = s;
    json = j;
    this.d2 = d2Contract;
  }

  /** 检查本次请求并决定是否继续后续过滤链。 */
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (ShareStaticResources.matches(request)) {
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("Referrer-Policy", "no-referrer");
      response.setHeader(
          "Content-Security-Policy",
          "default-src 'none'; script-src 'self'; style-src 'self'; img-src https:; connect-src"
              + " 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
      chain.doFilter(request, response);
      return;
    }
    if (PaymentNotificationRoutes.matches(request)) {
      response.setHeader("Cache-Control", "no-store");
      chain.doFilter(request, response);
      return;
    }
    request.setAttribute("traceId", Ids.next());
    ContentCachingResponseWrapper cached =
        d2.binary(d2.route(request.getRequestURI(), request.getMethod()))
            ? null
            : new ContentCachingResponseWrapper(response);
    HttpServletResponse out = cached == null ? response : cached;
    out.setHeader("Cache-Control", "no-store");
    out.setHeader("X-Request-Id", (String) request.getAttribute("traceId"));
    out.setCharacterEncoding("UTF-8");
    String path = request.getRequestURI();
    long start = System.nanoTime();
    HttpServletRequest r = request;
    try {
      Route route = d2.route(path, r.getMethod());
      Route anyRoute = d2.route(path);
      r.setAttribute("factoryApi", d2.factory(anyRoute));
      r.setAttribute(
          "diagnosticRoute",
          anyRoute != null ? anyRoute.template() : ROUTES.containsKey(path) ? path : "unmapped");
      String method = route == null ? ROUTES.get(path) : route.method();
      if (route == null && anyRoute != null && !r.getMethod().equals(ROUTES.get(path))) {
        out.setHeader("Allow", path.equals("/api/admin/system/menus")?"GET, POST":d2.allowed(path));
        throw new ApiFailure(405, BusinessFailureReason.METHOD_NOT_ALLOWED.name(), BusinessFailureReason.METHOD_NOT_ALLOWED.name());
      }
      if (method == null) throw new ApiFailure(404, D2Codes.CODE_NOT_FOUND, D2Codes.CODE_RESOURCE_NOT_FOUND);
      if (!method.equals(r.getMethod())) {
        out.setHeader("Allow", method);
        throw new ApiFailure(405, BusinessFailureReason.METHOD_NOT_ALLOWED.name(), BusinessFailureReason.METHOD_NOT_ALLOWED.name());
      }
      boolean upload =
          route != null && (path.endsWith("-materials") || path.endsWith("/evidence/uploads") || route.operation().path("requestBody").path("content").has("multipart/form-data"));
      if (route != null) {
        if (!upload) d2.parameters(r, route);
        else if (r.getQueryString() != null && !r.getQueryString().isEmpty())
          throw ApiFailure.invalid();
      } else if (r.getQueryString() != null && !r.getQueryString().isEmpty())
        throw ApiFailure.invalid();
      String activity = r.getHeader("X-Session-Activity");
      if (activity != null && !Set.of("interactive", "background").contains(activity))
        throw ApiFailure.invalid();
      if ("cross-site".equals(r.getHeader("Sec-Fetch-Site"))) throw ApiFailure.invalid();
      if (upload
          && (r.getContentType() == null
              || !r.getContentType().toLowerCase(Locale.ROOT).startsWith("multipart/form-data;")))
        throw new ApiFailure(415, D2Codes.CODE_UNSUPPORTED_MEDIA_TYPE, D2Codes.CODE_CONTENT_TYPE_UNSUPPORTED);
      if (upload && r.getContentLengthLong() > 6 * 1024 * 1024)
        throw new ApiFailure(413, D2Codes.CODE_PAYLOAD_TOO_LARGE, D2Codes.CODE_PAYLOAD_TOO_LARGE);
      int limit =
          "POST".equals(r.getMethod())
                  && (Set.of(
                              "/api/me/factory-onboarding",
                              "/api/me/factory-profile-changes",
                              "/api/me/factory-props")
                          .contains(path)
                      || path.matches("/api/me/factory-props/[^/]+/(resubmit|sn-adjustments)"))
              ? 128 * 1024
              : 8192;
      if (route != null
          && Set.of(
                  "issueCouponDistribution", "createCustomDraft", "saveCustomDraft", "saveCustomQuote", "deliverCustomOrder", "registerCustomPickup",
                  "updateScheme",
                  "reorderSchemeItems",
                  "saveChecklist",
                  "quoteCheckout",
                  "checkoutCoupons",
                  "submitCheckout",
                  "cancelRentalOrder")
              .contains(route.operation().path("operationId").asString())) limit = fullSaveMaxBytes;
      if (route != null
          && Set.of("factoryAttachEvidence", "buyerAttachEvidence", "saveExpense")
              .contains(route.operation().path("operationId").asString())) limit = 64 * 1024;
      byte[] body = upload ? new byte[0] : r.getInputStream().readNBytes(limit + 1);
      if (route != null && !upload && !route.operation().has("requestBody") && body.length != 0)
        throw ApiFailure.invalid();
      if (body.length > limit) {
        if (path.equals("/api/admin/auth/login")) throw ApiFailure.invalid();
        if (route != null) throw new ApiFailure(413, D2Codes.CODE_PAYLOAD_TOO_LARGE, D2Codes.CODE_PAYLOAD_TOO_LARGE);
        throw ApiFailure.invalid();
      }
      if (path.equals("/api/admin/auth/login")) {
        if (r.getContentType() == null
            || !r.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
          throw ApiFailure.invalid();
        JsonNode tree = json.readTree(body);
        if (tree == null
            || !tree.isObject()
            || tree.size() != 4
            || !tree.has("username")
            || !tree.has("password")
            || !tree.get("username").isString()
            || !tree.get("password").isString()) throw ApiFailure.invalid();
        if (!tree.path("username").isString()) throw ApiFailure.invalid();
        ((tools.jackson.databind.node.ObjectNode)tree).put("username",tree.get("username").asString().trim().toLowerCase(Locale.ROOT));
        d2.validate(route.operation().path("requestBody").path("content").path("application/json").path("schema"),tree);
        String name = tree.get("username").asString().trim().toLowerCase(Locale.ROOT),
            password = tree.get("password").asString();
        if (!name.matches("[a-z0-9_.-]{3,64}")
            || password.isEmpty()
            || password.getBytes(StandardCharsets.UTF_8).length > 72) throw ApiFailure.invalid();
      } else if (route != null && !upload) {
        if (route.operation().has("requestBody")
            && (r.getContentType() == null
                || !r.getContentType()
                    .split(";", 2)[0]
                    .trim()
                    .equalsIgnoreCase("application/json")))
          throw new ApiFailure(415, D2Codes.CODE_UNSUPPORTED_MEDIA_TYPE, D2Codes.CODE_CONTENT_TYPE_UNSUPPORTED);
        if (route.template().startsWith("/api/me/")
            && route.operation().has("requestBody")
            && route.operation().path("parameters").toString().contains("Idempotency-Key")
            && route.operation().path("requestBody").toString().contains("D3A_")) {
          com.mujian.backend.planning.utils.PlanningJson planningJson =
              new com.mujian.backend.planning.utils.PlanningJson(json);
          {
            JsonNode normalized =
                planningJson.normalize(
                    json.reader()
                        .with(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .readTree(body),
                    "");
            body = json.writeValueAsBytes(normalized);
            r.setAttribute(
                "planningHash",
                planningJson.hash(
                    route.operation().path("operationId").asString(),
                    r.getMethod(),
                    path,
                    normalized));
          }
        }
        if (route.operation().has("requestBody")
            && (route.operation().path("requestBody").toString().contains("D3B_")
                || route.operation().path("requestBody").toString().contains("D4_")
                || route.operation().path("requestBody").toString().contains("D6_")
                || route.operation().path("requestBody").toString().contains("D5_"))
            && !route.operation().path("operationId").asString().equals("submitSkuChange")) {
          com.mujian.backend.order.utils.TradingJson tradingJson =
              new com.mujian.backend.order.utils.TradingJson(json);
          JsonNode normalized =
              tradingJson.normalize(
                  json.reader()
                      .with(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                      .readTree(body),
                  "");
          body = json.writeValueAsBytes(normalized);
          r.setAttribute(
              "tradingHash",
              tradingJson.hash(
                  route.operation().path("operationId").asString(),
                  r.getMethod(),
                  path,
                  normalized));
        }
        if ("createAdminAccount".equals(route.operation().path("operationId").asString())) {
          JsonNode account = json.reader()
              .with(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).readTree(body);
          if (account instanceof tools.jackson.databind.node.ObjectNode object
              && object.path("username").isString()) {
            object.put("username", object.get("username").asString().trim().toLowerCase(Locale.ROOT));
            body = json.writeValueAsBytes(object);
          }
        }
        // 主动发券先走原 Cookie / CSRF 和发券权限，再判定名单领域错误。
        if (!"issueCouponDistribution".equals(route.operation().path("operationId").asString())) d2.body(route, body);
      } else if (body.length > 0) throw ApiFailure.invalid();
      if (!upload) r = new CachedRequest(r, body);
      chain.doFilter(r, out);
      if (out.getStatus() == 200
          && "GET".equals(method)
          && "interactive".equals(activity)
          && !path.endsWith("/auth/csrf")) sessionService.touch(sessionService.from(r));
    } catch (Exception e) {
      ApiFailure apiFailure =
          e instanceof tools.jackson.core.JacksonException
              ? ApiFailure.invalid()
              : FailureResolver.resolve(e);
      FailureDiagnostics.request(r, apiFailure, e);
      if (out.isCommitted()) {
        // 已开始传输的文件只能结束流，不能追加JSON或重置响应。
        throw e instanceof IOException ioException
            ? ioException
            : new IOException("committed response interrupted", e);
      }
      out.resetBuffer();
      out.setHeader("Content-Length", null);
      out.setHeader("Content-Disposition", null);
      out.setStatus(apiFailure.status);
      if (apiFailure.status == 429) out.setHeader("Retry-After", apiFailure.retryAfterSeconds.toString());
      out.setContentType("application/json");
      out.setCharacterEncoding("UTF-8");
      out.getWriter().write(json.writeValueAsString(ApiReply.error(r, apiFailure)));
    }
    out.setHeader("Cache-Control", "no-store");
    out.setHeader("X-Request-Id", (String) r.getAttribute("traceId"));
    if (cached != null) cached.copyBodyToResponse();
    LOG.info(
        "event=api_result path={} status={} traceId={} durationMs={}",
        ROUTES.containsKey(path)
            ? path
            : d2.route(path) != null ? d2.route(path).template() : "unknown",
        out.getStatus(),
        r.getAttribute("traceId"),
        (System.nanoTime() - start) / 1_000_000);
  }

  private static final class CachedRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedRequest(HttpServletRequest r, byte[] b) {
      super(r);
      body = b;
    }

    /** 提供可重复读取的请求输入流。 */
    public ServletInputStream getInputStream() {
      ByteArrayInputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        /** 从缓冲请求体读取下一个字节。 */
        public int read() {
          return in.read();
        }

        /** 判断缓冲请求体是否已读完。 */
        public boolean isFinished() {
          return in.available() == 0;
        }

        /** 报告输入流是否可继续读取。 */
        public boolean isReady() {
          return true;
        }

        /** 处理异步读取监听器注册。 */
        public void setReadListener(ReadListener l) {
          throw new UnsupportedOperationException();
        }
      };
    }

    /** 按请求字符集读取缓冲内容。 */
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
