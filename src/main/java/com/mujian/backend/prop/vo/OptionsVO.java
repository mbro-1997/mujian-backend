package com.mujian.backend.prop.vo;

import java.util.List;

/** 来自D2-C接口契约 Options，不是数据库实体。 */
public class OptionsVO {
  private List<OptionVO> types;

  private List<OptionVO> styles;

  private List<OptionVO> fireResistance;

  /** 读取道具类型选项，供接口结果展示使用。 */
  public List<OptionVO> getTypes() {
    return types;
  }

  /** 接收道具类型选项，保持接口结果展示的字段含义。 */
  public void setTypes(List<OptionVO> optionVOList) {
    types = optionVOList;
  }

  /** 读取有序风格集合，供接口结果展示使用。 */
  public List<OptionVO> getStyles() {
    return styles;
  }

  /** 接收有序风格集合，保持接口结果展示的字段含义。 */
  public void setStyles(List<OptionVO> optionVOList) {
    styles = optionVOList;
  }

  /** 读取防火性能选项，供接口结果展示使用。 */
  public List<OptionVO> getFireResistance() {
    return fireResistance;
  }

  /** 接收防火性能选项，保持接口结果展示的字段含义。 */
  public void setFireResistance(List<OptionVO> optionVOList) {
    fireResistance = optionVOList;
  }

  /** 设置道具类型选项并返回当前对象，供连续组装数据。 */
  public OptionsVO types(List<OptionVO> optionVOList) {
    setTypes(optionVOList);
    return this;
  }

  /** 设置有序风格集合并返回当前对象，供连续组装数据。 */
  public OptionsVO styles(List<OptionVO> optionVOList) {
    setStyles(optionVOList);
    return this;
  }

  /** 设置防火性能选项并返回当前对象，供连续组装数据。 */
  public OptionsVO fireResistance(List<OptionVO> optionVOList) {
    setFireResistance(optionVOList);
    return this;
  }
}
