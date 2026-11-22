package com.mujian.backend.prop.vo;

import java.util.List;

/** 来自D2-C接口契约 SkuRead，不是数据库实体。 */
public class SkuReadVO {
  private Integer packagingFactorBps;

  private String propName;

  private String typeCode;

  private List<String> styleCodes;

  private String sizeDesc;

  private String lengthCm;

  private String widthCm;

  private String heightCm;

  private String materialDesc;

  private String fireResistantCode;

  private String weightKg;

  private String transportSuggestion;

  private Integer dailyRentPriceFen;

  private Integer depositAmountFen;

  private List<ImageReadVO> images;

  private String remark;

  private String typeLabel;

  private List<String> styleLabels;

  private String fireResistantLabel;

  /** 读取道具名称，供接口结果展示使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持接口结果展示的字段含义。 */
  public void setPropName(String value) {
    propName = value;
  }

  /** 读取类别编码，供接口结果展示使用。 */
  public String getTypeCode() {
    return typeCode;
  }

  /** 接收类别编码，保持接口结果展示的字段含义。 */
  public void setTypeCode(String value) {
    typeCode = value;
  }

  /** 读取styleCodes，供接口结果展示使用。 */
  public List<String> getStyleCodes() {
    return styleCodes;
  }

  /** 接收styleCodes，保持接口结果展示的字段含义。 */
  public void setStyleCodes(List<String> value) {
    styleCodes = value;
  }

  /** 读取尺寸描述，供接口结果展示使用。 */
  public String getSizeDesc() {
    return sizeDesc;
  }

  /** 接收尺寸描述，保持接口结果展示的字段含义。 */
  public void setSizeDesc(String value) {
    sizeDesc = value;
  }

  /** 读取长，单位 cm，供接口结果展示使用。 */
  public String getLengthCm() {
    return lengthCm;
  }

  /** 接收长，单位 cm，保持接口结果展示的字段含义。 */
  public void setLengthCm(String value) {
    lengthCm = value;
  }

  /** 读取宽，单位 cm，供接口结果展示使用。 */
  public String getWidthCm() {
    return widthCm;
  }

  /** 接收宽，单位 cm，保持接口结果展示的字段含义。 */
  public void setWidthCm(String value) {
    widthCm = value;
  }

  /** 读取高，单位 cm，供接口结果展示使用。 */
  public String getHeightCm() {
    return heightCm;
  }

  /** 接收高，单位 cm，保持接口结果展示的字段含义。 */
  public void setHeightCm(String value) {
    heightCm = value;
  }

  /** 读取材质，供接口结果展示使用。 */
  public String getMaterialDesc() {
    return materialDesc;
  }

  /** 接收材质，保持接口结果展示的字段含义。 */
  public void setMaterialDesc(String value) {
    materialDesc = value;
  }

  /** 读取是否阻燃编码，供接口结果展示使用。 */
  public String getFireResistantCode() {
    return fireResistantCode;
  }

  /** 接收是否阻燃编码，保持接口结果展示的字段含义。 */
  public void setFireResistantCode(String value) {
    fireResistantCode = value;
  }

  /** 读取重量，单位 kg，供接口结果展示使用。 */
  public String getWeightKg() {
    return weightKg;
  }

  /** 接收重量，单位 kg，保持接口结果展示的字段含义。 */
  public void setWeightKg(String value) {
    weightKg = value;
  }

  /** 读取运输建议，供接口结果展示使用。 */
  public String getTransportSuggestion() {
    return transportSuggestion;
  }

  /** 接收运输建议，保持接口结果展示的字段含义。 */
  public void setTransportSuggestion(String value) {
    transportSuggestion = value;
  }

  /** 读取日租金，单位分，供接口结果展示使用。 */
  public Integer getDailyRentPriceFen() {
    return dailyRentPriceFen;
  }

  /** 接收日租金，单位分，保持接口结果展示的字段含义。 */
  public void setDailyRentPriceFen(Integer value) {
    dailyRentPriceFen = value;
  }

  /** 读取押金，单位分，供接口结果展示使用。 */
  public Integer getDepositAmountFen() {
    return depositAmountFen;
  }

  /** 接收押金，单位分，保持接口结果展示的字段含义。 */
  public void setDepositAmountFen(Integer value) {
    depositAmountFen = value;
  }

  /** 读取图片引用集合，供接口结果展示使用。 */
  public List<ImageReadVO> getImages() {
    return images;
  }

  /** 接收图片引用集合，保持接口结果展示的字段含义。 */
  public void setImages(List<ImageReadVO> imageReadVOList) {
    images = imageReadVOList;
  }

  /** 读取备注，供接口结果展示使用。 */
  public String getRemark() {
    return remark;
  }

  /** 接收备注，保持接口结果展示的字段含义。 */
  public void setRemark(String value) {
    remark = value;
  }

  /** 读取typeLabel，供接口结果展示使用。 */
  public String getTypeLabel() {
    return typeLabel;
  }

  /** 接收typeLabel，保持接口结果展示的字段含义。 */
  public void setTypeLabel(String value) {
    typeLabel = value;
  }

  /** 读取styleLabels，供接口结果展示使用。 */
  public List<String> getStyleLabels() {
    return styleLabels;
  }

  /** 接收styleLabels，保持接口结果展示的字段含义。 */
  public void setStyleLabels(List<String> value) {
    styleLabels = value;
  }

  /** 读取fireResistantLabel，供接口结果展示使用。 */
  public String getFireResistantLabel() {
    return fireResistantLabel;
  }

  /** 接收fireResistantLabel，保持接口结果展示的字段含义。 */
  public void setFireResistantLabel(String value) {
    fireResistantLabel = value;
  }

  /** 设置道具名称并返回当前对象，供连续组装数据。 */
  public SkuReadVO propName(String value) {
    setPropName(value);
    return this;
  }

  /** 设置类别编码并返回当前对象，供连续组装数据。 */
  public SkuReadVO typeCode(String value) {
    setTypeCode(value);
    return this;
  }

  /** 设置styleCodes并返回当前对象，供连续组装数据。 */
  public SkuReadVO styleCodes(List<String> value) {
    setStyleCodes(value);
    return this;
  }

  /** 设置尺寸描述并返回当前对象，供连续组装数据。 */
  public SkuReadVO sizeDesc(String value) {
    setSizeDesc(value);
    return this;
  }

  /** 设置长，单位 cm并返回当前对象，供连续组装数据。 */
  public SkuReadVO lengthCm(String value) {
    setLengthCm(value);
    return this;
  }

  /** 设置宽，单位 cm并返回当前对象，供连续组装数据。 */
  public SkuReadVO widthCm(String value) {
    setWidthCm(value);
    return this;
  }

  /** 设置高，单位 cm并返回当前对象，供连续组装数据。 */
  public SkuReadVO heightCm(String value) {
    setHeightCm(value);
    return this;
  }

  /** 设置材质并返回当前对象，供连续组装数据。 */
  public SkuReadVO materialDesc(String value) {
    setMaterialDesc(value);
    return this;
  }

  /** 设置是否阻燃编码并返回当前对象，供连续组装数据。 */
  public SkuReadVO fireResistantCode(String value) {
    setFireResistantCode(value);
    return this;
  }

  /** 设置重量，单位 kg并返回当前对象，供连续组装数据。 */
  public SkuReadVO weightKg(String value) {
    setWeightKg(value);
    return this;
  }

  /** 设置运输建议并返回当前对象，供连续组装数据。 */
  public SkuReadVO transportSuggestion(String value) {
    setTransportSuggestion(value);
    return this;
  }

  /** 设置日租金，单位分并返回当前对象，供连续组装数据。 */
  public SkuReadVO dailyRentPriceFen(Integer value) {
    setDailyRentPriceFen(value);
    return this;
  }

  /** 设置押金，单位分并返回当前对象，供连续组装数据。 */
  public SkuReadVO depositAmountFen(Integer value) {
    setDepositAmountFen(value);
    return this;
  }

  /** 设置图片引用集合并返回当前对象，供连续组装数据。 */
  public SkuReadVO images(List<ImageReadVO> imageReadVOList) {
    setImages(imageReadVOList);
    return this;
  }

  /** 设置备注并返回当前对象，供连续组装数据。 */
  public SkuReadVO remark(String value) {
    setRemark(value);
    return this;
  }

  /** 设置typeLabel并返回当前对象，供连续组装数据。 */
  public SkuReadVO typeLabel(String value) {
    setTypeLabel(value);
    return this;
  }

  /** 设置styleLabels并返回当前对象，供连续组装数据。 */
  public SkuReadVO styleLabels(List<String> value) {
    setStyleLabels(value);
    return this;
  }

  /** 设置fireResistantLabel并返回当前对象，供连续组装数据。 */
  public SkuReadVO fireResistantLabel(String value) {
    setFireResistantLabel(value);
    return this;
  }

  /** 读取包装体积系数，历史未知值保持为空。 */
  public Integer getPackagingFactorBps() {
    return packagingFactorBps;
  }

  /** 保存审核确认的包装体积系数。 */
  public void setPackagingFactorBps(Integer value) {
    packagingFactorBps = value;
  }
}
