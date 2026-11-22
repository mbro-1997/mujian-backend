package com.mujian.backend.material.vo;

/** 实名联查权限分支；未授权分支只返回access，不伪造敏感字段。 */
public sealed interface RealnameSensitiveAccessVO {
  record Restricted(String access) implements RealnameSensitiveAccessVO {}

  record Granted(
      String access, String applicationId, String fullName, String fullIdCard, String auditId)
      implements RealnameSensitiveAccessVO {}
}
