package com.mujian.backend.common.security;

import com.mujian.backend.common.api.ApiFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map.Entry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class D2Contract {
  private final JsonNode root;
  private final ObjectMapper json;

  public D2Contract(ObjectMapper j) throws java.io.IOException {
    json = j;
    try (InputStream in = getClass().getResourceAsStream("/contracts/d2a.openapi.json")) {
      root = j.readTree(in);
    }
    for (String batch : List.of("b", "c", "d3a", "d3b", "d4", "d5", "d6", "d7")) {
      try (InputStream in =
          getClass()
              .getResourceAsStream(
                  "/contracts/"
                      + (batch.startsWith("d") ? batch : "d2" + batch)
                      + ".openapi.json")) {
        String ns = batch.toUpperCase(Locale.ROOT) + "_";
        JsonNode next =
            j.readTree(
                j.writeValueAsString(j.readTree(in))
                    .replace("#/components/schemas/", "#/components/schemas/" + ns));
        ObjectNode paths = (tools.jackson.databind.node.ObjectNode) root.get("paths");
        for (Entry<String, JsonNode> entry : next.path("paths").properties()) {
          ObjectNode methods =
              paths.has(entry.getKey())
                  ? (ObjectNode) paths.get(entry.getKey())
                  : json.createObjectNode();
          for (Entry<String, JsonNode> operation : entry.getValue().properties()) {
            if (methods.has(operation.getKey())
                && !(batch.equals("d6") && Set.of(
                        "tradingOperation", "adminTradingOperation", "adminPayment", "adminRefunds",
                        "adminRefund", "myRefund", "factorySettlementItems", "adminSettlementItems")
                    .contains(operation.getValue().path("operationId").asString()) && methods.get(operation.getKey()).path("operationId").equals(operation.getValue().path("operationId")))
                && !(batch.equals("d5") && Set.of("confirmReturn","adminCloseReturn","factorySettlementItems","adminSettlementItems","sendReturnShipment","adminEvidence","adminEvidenceView","adminFulfillment","adminFulfillmentRisks","adminPayment","adminPayments","adminRefund","adminRefunds","adminRentalOrder","adminRentalOrders","adminTradingOperation","buyerAttachEvidence","buyerEvidence","buyerEvidenceView","buyerFulfillment","buyerRemoveDraftEvidence","buyerUploadEvidence","factoryAttachEvidence","factoryEvidence","factoryEvidenceView","factoryFulfillment","factoryOrder","factoryOrderOverview","factoryRemoveDraftEvidence","factoryUploadEvidence","launchCheckoutPayment","listFactoryOrders","listMyOrders","myOrder","myRefund","tradingOperation").contains(operation.getValue().path("operationId").asString())
                    && methods.get(operation.getKey()).path("operationId").equals(operation.getValue().path("operationId")))
                && !(batch.equals("d4")
                    && Set.of(
                            "tradingOperation",
                            "adminTradingOperation",
                            "listMyOrders",
                            "myOrder",
                            "listFactoryOrders",
                            "factoryOrder",
                            "factoryOrderOverview",
                            "adminRentalOrders",
                            "adminRentalOrder")
                        .contains(operation.getValue().path("operationId").asString())
                    && methods
                        .get(operation.getKey())
                        .path("operationId")
                        .equals(operation.getValue().path("operationId"))))
              throw new IllegalStateException(
                  "duplicate contract operation: " + operation.getKey() + " " + entry.getKey());
            methods.set(operation.getKey(), operation.getValue());
          }
          paths.set(entry.getKey(), methods);
        }
        ObjectNode schemas =
            (tools.jackson.databind.node.ObjectNode) root.path("components").get("schemas");
        for (Entry<String, JsonNode> e : next.path("components").path("schemas").properties())
          schemas.set(ns + e.getKey(), e.getValue());
      }
    }
  }

  public record Route(String template, String method, JsonNode operation) {}

  /** 根据路径匹配接口规则。 */
  public Route route(String path) {
    return route(path, null);
  }

  /** 精确静态路径优先；同路径GET/POST分别注册，不能取第一个操作充当全部路由。 */
  public Route route(String path, String method) {
    ArrayList<Entry<String, JsonNode>> entries = new ArrayList<>(root.path("paths").properties());
    entries.sort(Comparator.comparingInt(e -> e.getKey().contains("{") ? 1 : 0));
    for (Entry<String, JsonNode> e : entries) {
      if (!path.matches(e.getKey().replaceAll("\\{[^}]+}", "[^/]+"))) continue;
      if (method != null) {
        JsonNode op = e.getValue().get(method.toLowerCase(Locale.ROOT));
        return op == null ? null : new Route(e.getKey(), method, op);
      }
      Entry<String, JsonNode> op = e.getValue().properties().iterator().next();
      return new Route(e.getKey(), op.getKey().toUpperCase(Locale.ROOT), op.getValue());
    }
    return null;
  }

  /** 根据成功媒体类型识别无需整份缓存的文件操作。 */
  public boolean binary(Route route) {
    if (route == null) return false;
    JsonNode content = route.operation().path("responses").path("200").path("content");
    return content.has("application/pdf") || content.has("text/csv")
        || content.has("image/png") || content.has("image/jpeg");
  }

  /** 判断响应组合或输入值是否在契约范围内。 */
  public String allowed(String path) {
    D2Contract.Route route = route(path);
    if (route == null) return null;
    return String.join(
        ", ",
        root.path("paths").get(route.template()).propertyNames().stream()
            .map(x -> x.toUpperCase(Locale.ROOT))
            .sorted()
            .toList());
  }

  /** 核对身份后读取指定工厂。 */
  public boolean factory(Route route) {
    return route != null
        && (route.template().contains("/factory")
            || route.template().contains("/factories")
            || route.template().contains("/prop")
            || route.template().contains("/inventory-reservations"));
  }

  /** 准备当前调用需要的参数。 */
  public void parameters(HttpServletRequest r, Route route) {
    HashSet<String> allowed = new HashSet<String>();
    String[] template = route.template().split("/"), actual = r.getRequestURI().split("/");
    for (JsonNode p : route.operation().path("parameters")) {
      String name = p.path("name").asString(), kind = p.path("in").asString();
      String value = null;
      if (kind.equals("query")) {
        allowed.add(name);
        value = r.getParameter(name);
        if (p.path("schema").path("type").asString("").equals("array")) {
          String[] values = r.getParameterValues(name);
          if (values == null) {
            if (p.path("required").asBoolean(false)) throw ApiFailure.invalid();
            continue;
          }
          validate(p.path("schema"), json.valueToTree(Arrays.asList(values)));
          continue;
        }
        if (r.getParameterValues(name) != null && r.getParameterValues(name).length != 1)
          throw ApiFailure.invalid();
      } else if (kind.equals("header")) value = r.getHeader(name);
      else
        for (int i = 0; i < template.length; i++)
          if (template[i].equals("{" + name + "}")) value = actual[i];
      if (value == null) {
        if (p.path("required").asBoolean(false)) throw ApiFailure.invalid();
        continue;
      }
      JsonNode schema = p.path("schema"), node = json.valueToTree(value);
      if (schema.path("type").asString("").equals("integer")) {
        try {
          if (!value.matches("[1-9][0-9]*")) throw ApiFailure.invalid();
          node = json.valueToTree(Integer.parseInt(value));
        } catch (NumberFormatException e) {
          throw ApiFailure.invalid();
        }
      }
      if (schema.path("type").asString("").equals("boolean")) {
        if (!Set.of("true", "false").contains(value)) throw ApiFailure.invalid();
        node = json.valueToTree(Boolean.parseBoolean(value));
      }
      validate(schema, node);
    }
    if (!allowed.containsAll(r.getParameterMap().keySet())) throw ApiFailure.invalid();
  }

  /** 按契约读取请求体。 */
  public void body(Route route, byte[] bytes) {
    JsonNode body = route.operation().path("requestBody");
    if (body.isMissingNode()) {
      if (bytes.length != 0) throw ApiFailure.invalid();
      return;
    }
    if (bytes.length == 0) throw ApiFailure.invalid();
    validate(body.path("content").path("application/json").path("schema"), json.readTree(bytes));
  }

  /** 拒绝不符合接口约束的请求。 */
  public void validate(JsonNode s, JsonNode n) {
    if (s.isBoolean()) {
      if (!s.asBoolean()) throw ApiFailure.invalid();
      return;
    }
    if (s.has("$ref")) {
      String pointer = s.get("$ref").asString();
      if (!pointer.startsWith("#/"))
        throw new IllegalStateException("unsupported local schema reference");
      JsonNode target = root.at(pointer.substring(1));
      if (target.isMissingNode()) throw new IllegalStateException("missing schema reference");
      validate(target, n);
      // OpenAPI 3.1的$ref同级约束仍须校验；递归对象随instance子节点自然下降。
    }
    if (n == null) throw ApiFailure.invalid();
    if (s.has("oneOf")) {
      int ok = 0;
      for (JsonNode c : s.get("oneOf"))
        try {
          validate(c, n);
          ok++;
        } catch (ApiFailure ignored) {
        }
      if (ok != 1) throw ApiFailure.invalid();
    }
    if (s.has("allOf")) for (JsonNode part : s.get("allOf")) validate(part, n);
    if (s.has("anyOf")) {
      boolean validAlternative = false;
      for (JsonNode part : s.get("anyOf"))
        try {
          validate(part, n);
          validAlternative = true;
        } catch (ApiFailure ignored) {
        }
      if (!validAlternative) throw ApiFailure.invalid();
    }
    if (s.has("if")) {
      boolean matches = true;
      try {
        validate(s.get("if"), n);
      } catch (ApiFailure ignored) {
        matches = false;
      }
      if (matches && s.has("then")) validate(s.get("then"), n);
      if (!matches && s.has("else")) validate(s.get("else"), n);
    }
    if (s.has("not")) {
      boolean matches = true;
      try {
        validate(s.get("not"), n);
      } catch (ApiFailure ignored) {
        matches = false;
      }
      if (matches) throw ApiFailure.invalid();
    }
    if (s.has("const") && !s.get("const").equals(n)) throw ApiFailure.invalid();
    if (s.has("enum")) {
      boolean ok = false;
      for (JsonNode x : s.get("enum")) ok |= x.equals(n);
      if (!ok) throw ApiFailure.invalid();
    }
    String t = s.path("type").asString("");
    boolean valid =
        switch (t) {
          case "object" -> n.isObject();
          case "array" -> n.isArray();
          case "string" -> n.isString();
          case "integer" -> n.isIntegralNumber();
          case "boolean" -> n.isBoolean();
          case "null" -> n.isNull();
          default -> true;
        };
    if (!valid) throw ApiFailure.invalid();
    if (n.isObject()) {
      for (JsonNode req : s.path("required"))
        if (!n.has(req.asString())) throw ApiFailure.invalid();
      for (Entry<String, JsonNode> e : n.properties()) {
        JsonNode prop = s.path("properties").get(e.getKey());
        if (prop == null) {
          if (s.has("additionalProperties")) validate(s.get("additionalProperties"), e.getValue());
        } else validate(prop, e.getValue());
      }
    }
    if (n.isString()) {
      String v = n.asString();
      int len = v.codePointCount(0, v.length());
      if (len < s.path("minLength").asInt(0)
          || len > s.path("maxLength").asInt(Integer.MAX_VALUE)
          || s.has("pattern")
              && !java.util.regex.Pattern.compile(s.get("pattern").asString()).matcher(v).find())
        throw ApiFailure.invalid();
      if (s.path("format").asString("").equals("date")) {
        try {
          java.time.LocalDate.parse(v);
        } catch (Exception e) {
          throw ApiFailure.invalid();
        }
      }
      if (s.path("format").asString("").equals("date-time"))
        try {
          java.time.OffsetDateTime.parse(v);
        } catch (Exception e) {
          throw ApiFailure.invalid();
        }
    }
    if (n.isNumber()) {
      BigDecimal value = new java.math.BigDecimal(n.asText());
      if (s.has("minimum")
              && value.compareTo(new java.math.BigDecimal(s.get("minimum").asText())) < 0
          || s.has("maximum")
              && value.compareTo(new java.math.BigDecimal(s.get("maximum").asText())) > 0)
        throw ApiFailure.invalid();
    }
    if (n.isArray()) {
      if (s.has("contains")) {
        int matching = 0;
        for (JsonNode item : n) {
          try {
            validate(s.get("contains"), item);
            matching++;
          } catch (ApiFailure ignored) {
          }
        }
        if (matching < s.path("minContains").asInt(1)
            || matching > s.path("maxContains").asInt(Integer.MAX_VALUE))
          throw ApiFailure.invalid();
      }

      if (n.size() < s.path("minItems").asInt(0)
          || n.size() > s.path("maxItems").asInt(Integer.MAX_VALUE)) throw ApiFailure.invalid();
      HashSet<JsonNode> seen = new HashSet<JsonNode>();
      int index = 0;
      JsonNode prefix = s.path("prefixItems");
      for (JsonNode v : n) {
        if (s.path("uniqueItems").asBoolean(false) && !seen.add(v)) throw ApiFailure.invalid();
        if (index < prefix.size()) validate(prefix.get(index), v);
        else if (s.has("items")) validate(s.get("items"), v);
        index++;
      }
    }
  }
}
