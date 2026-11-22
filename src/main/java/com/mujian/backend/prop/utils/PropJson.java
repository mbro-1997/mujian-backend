package com.mujian.backend.prop.utils;

import com.mujian.backend.common.api.ApiFailure;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

@Component
public class PropJson {
  public final ObjectMapper objectMapper;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 将业务快照或结果序列化为JSON。 */
  public String write(Object value) {
    return objectMapper.writeValueAsString(value);
  }

  /** 把JSON对象还原为键值集合供摘要处理。 */
  @SuppressWarnings("unchecked")
  public Map<String, Object> object(String value) {
    return objectMapper.readValue(value, Map.class);
  }

  /** 读取JSON字符串集合，空值按空集合处理。 */
  @SuppressWarnings("unchecked")
  public List<String> strings(String value) {
    return value == null ? List.of() : objectMapper.readValue(value, List.class);
  }

  /** 把对象转换为摘要计算所需的JSON键值结构。 */
  public Map<String, Object> map(Object value) {
    return object(write(value));
  }

  /** 按稳定规范计算业务请求摘要，供幂等内容核对。 */
  public String hash(String operation, String target, Object input) {
    Map<String, Object> raw = map(input);
    if (raw.containsKey("snIds") && raw.get("snIds") != null) {
      ArrayList<String> ids = new ArrayList<>((List<String>) raw.get("snIds"));
      if (new HashSet<>(ids).size() != ids.size()) throw ApiFailure.invalid();
      Collections.sort(ids);
      raw.put("snIds", ids);
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      write(
                              normalize(
                                  Map.of(
                                      "operation",
                                      operation,
                                      "target",
                                      target == null ? "" : target,
                                      "body",
                                      raw)))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException noSuchAlgorithmException) {
      throw new IllegalStateException(noSuchAlgorithmException);
    }
  }

  /** 递归整理请求摘要输入，保持既定键序和文本规则。 */
  private Object normalize(Object v) {
    if (v instanceof Map<?, ?> m) {
      TreeMap<String, Object> x = new TreeMap<String, Object>();
      m.forEach((k, z) -> x.put((String) k, normalize(z)));
      return x;
    }
    if (v instanceof List<?> values) {
      List<Object> normalizedValues = new ArrayList<>();
      for (Object value : values) normalizedValues.add(normalize(value));
      return normalizedValues;
    }
    if (v instanceof String s) return s.trim();
    return v;
  }

  /** 将Java属性名转成快照约定的下划线名称。 */
  public static String snake(String s) {
    return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }

  /** 将数据库字段名转成Java属性名。 */
  public static String camel(String s) {
    StringBuilder stringBuilder = new StringBuilder();
    boolean cap = false;
    for (char c : s.toCharArray()) {
      if (c == '_') {
        cap = true;
        continue;
      }
      stringBuilder.append(cap ? Character.toUpperCase(c) : c);
      cap = false;
    }
    return stringBuilder.toString();
  }
}
