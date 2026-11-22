package com.mujian.backend.planning.utils;

import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.*;

/** JSON规范化仅用于本批摘要与输入，不承担业务流程。 */
@Component
public class PlanningJson {
  private final ObjectMapper objectMapper;

  /** 使用框架同一序列化配置。 */
  public PlanningJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 递归排序对象键；数组保持顺序；可选空文本统一为null。 */
  public JsonNode normalize(JsonNode jsonNodeValue, String field) {
    if (jsonNodeValue == null) throw com.mujian.backend.common.api.ApiFailure.invalid();
    if (jsonNodeValue.isObject()) {
      ObjectNode objectNodeResult = objectMapper.createObjectNode();
      List<String> keys = new ArrayList<>(jsonNodeValue.propertyNames());
      Collections.sort(keys);
      for (String key : keys) objectNodeResult.set(key, normalize(jsonNodeValue.get(key), key));
      return objectNodeResult;
    }
    if (jsonNodeValue.isArray()) {
      ArrayNode arrayNodeResult = objectMapper.createArrayNode();
      for (JsonNode jsonNodeElement : jsonNodeValue)
        arrayNodeResult.add(normalize(jsonNodeElement, field));
      return arrayNodeResult;
    }
    if (jsonNodeValue.isString()) {
      String text = jsonNodeValue.asString().trim();
      if (text.isEmpty() && Set.of("description", "remark").contains(field))
        return NullNode.getInstance();
      return objectMapper.valueToTree(text);
    }
    return jsonNodeValue;
  }

  /** 摘要包含动作、方法、精确路径及规范正文，数组重排视为不同命令。 */
  public String hash(String operation, String method, String path, JsonNode jsonNodeBody) {
    ObjectNode objectNodeValue = objectMapper.createObjectNode();
    objectNodeValue.put("operationId", operation);
    objectNodeValue.put("method", method);
    objectNodeValue.put("path", path);
    objectNodeValue.set("body", jsonNodeBody);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(objectMapper.writeValueAsBytes(normalize(objectNodeValue, ""))));
    } catch (java.security.NoSuchAlgorithmException noSuchAlgorithmExceptionException) {
      throw new IllegalStateException(noSuchAlgorithmExceptionException);
    }
  }
}
