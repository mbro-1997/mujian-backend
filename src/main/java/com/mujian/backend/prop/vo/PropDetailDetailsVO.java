package com.mujian.backend.prop.vo;

import java.util.List;

/** 来自D2-C接口契约 PropDetailDetails，不是数据库实体。 */
public class PropDetailDetailsVO {
  private String propName;

  private String sizeDesc;

  private String lengthCm;

  private String widthCm;

  private String heightCm;

  private String materialDesc;

  private String weightKg;

  private String transportSuggestion;

  private Integer dailyRentPriceFen;

  private Integer depositAmountFen;

  private List<PublicImageViewVO> images;

  private OptionVO fireResistance;

  /** 读取道具名称，供接口结果展示使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持接口结果展示的字段含义。 */
  public void setPropName(String value) {
    propName = value;
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
  public List<PublicImageViewVO> getImages() {
    return images;
  }

  /** 接收图片引用集合，保持接口结果展示的字段含义。 */
  public void setImages(List<PublicImageViewVO> publicImageViewVOList) {
    images = publicImageViewVOList;
  }

  /** 读取防火性能选项，供接口结果展示使用。 */
  public OptionVO getFireResistance() {
    return fireResistance;
  }

  /** 接收防火性能选项，保持接口结果展示的字段含义。 */
  public void setFireResistance(OptionVO optionVO) {
    fireResistance = optionVO;
  }

  /** 设置道具名称并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO propName(String value) {
    setPropName(value);
    return this;
  }

  /** 设置尺寸描述并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO sizeDesc(String value) {
    setSizeDesc(value);
    return this;
  }

  /** 设置长，单位 cm并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO lengthCm(String value) {
    setLengthCm(value);
    return this;
  }

  /** 设置宽，单位 cm并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO widthCm(String value) {
    setWidthCm(value);
    return this;
  }

  /** 设置高，单位 cm并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO heightCm(String value) {
    setHeightCm(value);
    return this;
  }

  /** 设置材质并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO materialDesc(String value) {
    setMaterialDesc(value);
    return this;
  }

  /** 设置重量，单位 kg并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO weightKg(String value) {
    setWeightKg(value);
    return this;
  }

  /** 设置运输建议并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO transportSuggestion(String value) {
    setTransportSuggestion(value);
    return this;
  }

  /** 设置日租金，单位分并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO dailyRentPriceFen(Integer value) {
    setDailyRentPriceFen(value);
    return this;
  }

  /** 设置押金，单位分并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO depositAmountFen(Integer value) {
    setDepositAmountFen(value);
    return this;
  }

  /** 设置图片引用集合并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO images(List<PublicImageViewVO> publicImageViewVOList) {
    setImages(publicImageViewVOList);
    return this;
  }

  /** 设置防火性能选项并返回当前对象，供连续组装数据。 */
  public PropDetailDetailsVO fireResistance(OptionVO optionVO) {
    setFireResistance(optionVO);
    return this;
  }
}
