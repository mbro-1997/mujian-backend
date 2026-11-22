package com.mujian.backend.common.support;

public final class Ids {
  private Ids() {}

  /** 生成业务记录使用的唯一标识。 */
  public static String next() {
    return java.util.UUID.randomUUID().toString().replace("-", "");
  }
}
