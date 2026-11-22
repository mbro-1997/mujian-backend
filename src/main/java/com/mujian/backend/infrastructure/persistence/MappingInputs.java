package com.mujian.backend.infrastructure.persistence;

import java.util.*;

/** XML owns the fixed identifier lists; this helper validates values without constructing SQL. */
public final class MappingInputs {
  private MappingInputs() {}

  /** 校验动态选择的表是否在允许范围内。 */
  public static boolean table(String table, String allowed) {
    if (!Set.of(allowed.split(",")).contains(table)) throw new IllegalArgumentException();
    return true;
  }

  /** 校验待写字段均属于允许范围。 */
  public static boolean columns(Map<String, ?> values, String allowed) {
    if (!Set.of(allowed.split(",")).containsAll(values.keySet()))
      throw new IllegalArgumentException();
    return true;
  }

  /** JDBC's original projection selected the first row, including the no-row -> null contract. */
  public static <T> T first(List<T> rows) {
    return rows.isEmpty() ? null : rows.get(0);
  }
}
