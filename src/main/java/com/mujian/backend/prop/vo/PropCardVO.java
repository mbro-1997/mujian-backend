package com.mujian.backend.prop.vo;

import java.util.List;

/** 来自D2-C接口契约 PropCard，不是数据库实体。 */
public class PropCardVO {
  private String skuId;

  private String propName;

  private String assetType;

  private PublicImageViewVO cover;

  private OptionVO type;

  private List<OptionVO> styles;

  private Integer dailyRentPriceFen;

  private Integer depositAmountFen;

  private FactorySummaryVO factory;

  /** 读取道具标识，供接口结果展示使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持接口结果展示的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取道具名称，供接口结果展示使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持接口结果展示的字段含义。 */
  public void setPropName(String value) {
    propName = value;
  }

  /** 读取资产类型，供接口结果展示使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持接口结果展示的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取道具封面，供接口结果展示使用。 */
  public PublicImageViewVO getCover() {
    return cover;
  }

  /** 接收道具封面，保持接口结果展示的字段含义。 */
  public void setCover(PublicImageViewVO publicImageViewVO) {
    cover = publicImageViewVO;
  }

  /** 读取type，供接口结果展示使用。 */
  public OptionVO getType() {
    return type;
  }

  /** 接收type，保持接口结果展示的字段含义。 */
  public void setType(OptionVO optionVO) {
    type = optionVO;
  }

  /** 读取有序风格集合，供接口结果展示使用。 */
  public List<OptionVO> getStyles() {
    return styles;
  }

  /** 接收有序风格集合，保持接口结果展示的字段含义。 */
  public void setStyles(List<OptionVO> optionVOList) {
    styles = optionVOList;
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

  /** 读取公开工厂资料，供接口结果展示使用。 */
  public FactorySummaryVO getFactory() {
    return factory;
  }

  /** 接收公开工厂资料，保持接口结果展示的字段含义。 */
  public void setFactory(FactorySummaryVO factorySummaryVO) {
    factory = factorySummaryVO;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public PropCardVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置道具名称并返回当前对象，供连续组装数据。 */
  public PropCardVO propName(String value) {
    setPropName(value);
    return this;
  }

  /** 设置资产类型并返回当前对象，供连续组装数据。 */
  public PropCardVO assetType(String value) {
    setAssetType(value);
    return this;
  }

  /** 设置道具封面并返回当前对象，供连续组装数据。 */
  public PropCardVO cover(PublicImageViewVO publicImageViewVO) {
    setCover(publicImageViewVO);
    return this;
  }

  /** 设置type并返回当前对象，供连续组装数据。 */
  public PropCardVO type(OptionVO optionVO) {
    setType(optionVO);
    return this;
  }

  /** 设置有序风格集合并返回当前对象，供连续组装数据。 */
  public PropCardVO styles(List<OptionVO> optionVOList) {
    setStyles(optionVOList);
    return this;
  }

  /** 设置日租金，单位分并返回当前对象，供连续组装数据。 */
  public PropCardVO dailyRentPriceFen(Integer value) {
    setDailyRentPriceFen(value);
    return this;
  }

  /** 设置押金，单位分并返回当前对象，供连续组装数据。 */
  public PropCardVO depositAmountFen(Integer value) {
    setDepositAmountFen(value);
    return this;
  }

  /** 设置公开工厂资料并返回当前对象，供连续组装数据。 */
  public PropCardVO factory(FactorySummaryVO factorySummaryVO) {
    setFactory(factorySummaryVO);
    return this;
  }
}
