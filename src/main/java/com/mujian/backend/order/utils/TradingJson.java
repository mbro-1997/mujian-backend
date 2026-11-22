package com.mujian.backend.order.utils;

import com.mujian.backend.common.api.ApiFailure;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;

/** 交易摘要只规范化输入意图，不把当前报价或渠道状态写入摘要。 */
@Component
public class TradingJson {
  private final ObjectMapper objectMapper;

  /** 使用应用同一JSON配置。 */
  public TradingJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 对象键排序、配送按工厂排序，签名令牌原文保持不变。 */
  public JsonNode normalize(JsonNode node, String field) {
    if (node == null) throw ApiFailure.invalid();
    if (node.isObject()) {
      ObjectNode result = objectMapper.createObjectNode();
      List<String> keys = new ArrayList<>(node.propertyNames());
      Collections.sort(keys);
      for (String key : keys) result.set(key, normalize(node.get(key), key));
      return result;
    }
    if (node.isArray()) {
      List<JsonNode> values = new ArrayList<>();
      for (JsonNode value : node) values.add(normalize(value, field));
      if ("deliveries".equals(field))
        values.sort(Comparator.comparing(value -> value.path("factoryId").asString()));
      ArrayNode result = objectMapper.createArrayNode();
      for (JsonNode value : values) result.add(value);
      return result;
    }
    if (node.isString() && !Set.of("quoteToken", "confirmToken").contains(field)) {
      String value = node.asString().trim();
      if (value.isEmpty() && Set.of("buyerRemark", "remark").contains(field))
        return NullNode.getInstance();
      return objectMapper.valueToTree(value);
    }
    return node;
  }

  /** 摘要固定动作、方法、实际路径及已规范正文。 */
  public String hash(String operation, String method, String path, JsonNode body) {
    ObjectNode value = objectMapper.createObjectNode();
    value.put("operationId", operation);
    value.put("method", method);
    value.put("path", path);
    value.set("body", body);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(objectMapper.writeValueAsBytes(normalize(value, ""))));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
