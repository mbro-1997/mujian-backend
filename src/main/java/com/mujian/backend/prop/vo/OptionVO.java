package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 Option，不是数据库实体。 */
public class OptionVO {
  private String code;

  private String label;

  private Boolean enabled;

  private Boolean retained;

  /** 读取字典编码，供接口结果展示使用。 */
  public String getCode() {
    return code;
  }

  /** 接收字典编码，保持接口结果展示的字段含义。 */
  public void setCode(String value) {
    code = value;
  }

  /** 读取字典显示名称，供接口结果展示使用。 */
  public String getLabel() {
    return label;
  }

  /** 接收字典显示名称，保持接口结果展示的字段含义。 */
  public void setLabel(String value) {
    label = value;
  }

  /** 读取是否允许新业务选用，供接口结果展示使用。 */
  public Boolean getEnabled() {
    return enabled;
  }

  /** 接收是否允许新业务选用，保持接口结果展示的字段含义。 */
  public void setEnabled(Boolean value) {
    enabled = value;
  }

  /** 读取是否仅为历史保留值，供接口结果展示使用。 */
  public Boolean getRetained() {
    return retained;
  }

  /** 接收是否仅为历史保留值，保持接口结果展示的字段含义。 */
  public void setRetained(Boolean value) {
    retained = value;
  }

  /** 设置字典编码并返回当前对象，供连续组装数据。 */
  public OptionVO code(String value) {
    setCode(value);
    return this;
  }

  /** 设置字典显示名称并返回当前对象，供连续组装数据。 */
  public OptionVO label(String value) {
    setLabel(value);
    return this;
  }

  /** 设置是否允许新业务选用并返回当前对象，供连续组装数据。 */
  public OptionVO enabled(Boolean value) {
    setEnabled(value);
    return this;
  }

  /** 设置是否仅为历史保留值并返回当前对象，供连续组装数据。 */
  public OptionVO retained(Boolean value) {
    setRetained(value);
    return this;
  }
}
