package com.mujian.backend.common.enums;

/** 固定字典组编码；选项内容仍由运营字典管理。 */
public enum DictionaryGroup {
  PROP_SKU_TYPE("prop_sku-type"),
  PROP_SKU_STYLE("prop_sku_style-style"),
  PROP_SKU_FIRE_RESISTANT("prop_sku-fire_resistant");
  private final String code;

  /** 保存稳定字典组编码。 */
  DictionaryGroup(String code) {
    this.code = code;
  }

  /** 获取数据库使用的字典组编码。 */
  public String code() {
    return code;
  }

  /** 将持久化编码解析为业务字典组，拒绝未登记编码。 */
  public static DictionaryGroup fromCode(String code) {
    for (DictionaryGroup dictionaryGroup : values()) {
      if (dictionaryGroup.code.equals(code)) return dictionaryGroup;
    }
    throw new IllegalArgumentException("未登记的字典组：" + code);
  }
}
