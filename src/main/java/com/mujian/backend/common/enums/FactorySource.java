package com.mujian.backend.common.enums;

public enum FactorySource {
  ONBOARDING("factory_onboarding_audit", "factory-onboarding", "factory_onboarding_review"),
  CHANGE("factory_profile_change_audit", "factory-profile-change", "factory_profile_change_review"),
  PROFILE("factory_profile", "factory", null);
  public final String table, permission, caseType;

  FactorySource(String t, String p, String c) {
    table = t;
    permission = p;
    caseType = c;
  }

  /** 从已知编码解析来源类型。 */
  public static FactorySource from(String v) {
    for (FactorySource factorySource : values()) if (factorySource.table.equals(v)) return factorySource;
    throw com.mujian.backend.common.api.ApiFailure.invalid();
  }
}
