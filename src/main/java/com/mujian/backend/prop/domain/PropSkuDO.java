package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.math.BigDecimal;
import java.time.*;

/** prop_sku：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropSkuDO extends BaseDO {
  private Integer packagingFactorBps;

  /** 所属工厂 ID */
  private String factoryId;

  /** SKU 编码 */
  private String skuCode;

  /** 道具名称 */
  private String propName;

  /** 资产类型 */
  private String assetType;

  /** 主图 URL */
  private String imageUrl;

  /** 类别编码 */
  private String typeCode;

  /** 尺寸描述 */
  private String sizeDesc;

  /** 长，单位 cm */
  private BigDecimal lengthCm;

  /** 宽，单位 cm */
  private BigDecimal widthCm;

  /** 高，单位 cm */
  private BigDecimal heightCm;

  /** 材质 */
  private String materialDesc;

  /** 是否阻燃编码 */
  private String fireResistantCode;

  /** 重量，单位 kg */
  private BigDecimal weightKg;

  /** 运输建议 */
  private String transportSuggestion;

  /** 日租金，单位分 */
  private Integer dailyRentPriceFen;

  /** 押金，单位分 */
  private Integer depositAmountFen;

  /** SKU 状态 */
  private String skuStatus;

  /** 资料填充状态 */
  private String fillStatus;

  /** 备注 */
  private String remark;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 当前资料版本 */
  private Integer version;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 读取所属工厂 ID，供持久化映射使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收所属工厂 ID，保持持久化映射的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取SKU 编码，供持久化映射使用。 */
  public String getSkuCode() {
    return skuCode;
  }

  /** 接收SKU 编码，保持持久化映射的字段含义。 */
  public void setSkuCode(String value) {
    skuCode = value;
  }

  /** 读取道具名称，供持久化映射使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持持久化映射的字段含义。 */
  public void setPropName(String value) {
    propName = value;
  }

  /** 读取资产类型，供持久化映射使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持持久化映射的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取主图 URL，供持久化映射使用。 */
  public String getImageUrl() {
    return imageUrl;
  }

  /** 接收主图 URL，保持持久化映射的字段含义。 */
  public void setImageUrl(String value) {
    imageUrl = value;
  }

  /** 读取类别编码，供持久化映射使用。 */
  public String getTypeCode() {
    return typeCode;
  }

  /** 接收类别编码，保持持久化映射的字段含义。 */
  public void setTypeCode(String value) {
    typeCode = value;
  }

  /** 读取尺寸描述，供持久化映射使用。 */
  public String getSizeDesc() {
    return sizeDesc;
  }

  /** 接收尺寸描述，保持持久化映射的字段含义。 */
  public void setSizeDesc(String value) {
    sizeDesc = value;
  }

  /** 读取长，单位 cm，供持久化映射使用。 */
  public BigDecimal getLengthCm() {
    return lengthCm;
  }

  /** 接收长，单位 cm，保持持久化映射的字段含义。 */
  public void setLengthCm(BigDecimal value) {
    lengthCm = value;
  }

  /** 读取宽，单位 cm，供持久化映射使用。 */
  public BigDecimal getWidthCm() {
    return widthCm;
  }

  /** 接收宽，单位 cm，保持持久化映射的字段含义。 */
  public void setWidthCm(BigDecimal value) {
    widthCm = value;
  }

  /** 读取高，单位 cm，供持久化映射使用。 */
  public BigDecimal getHeightCm() {
    return heightCm;
  }

  /** 接收高，单位 cm，保持持久化映射的字段含义。 */
  public void setHeightCm(BigDecimal value) {
    heightCm = value;
  }

  /** 读取材质，供持久化映射使用。 */
  public String getMaterialDesc() {
    return materialDesc;
  }

  /** 接收材质，保持持久化映射的字段含义。 */
  public void setMaterialDesc(String value) {
    materialDesc = value;
  }

  /** 读取是否阻燃编码，供持久化映射使用。 */
  public String getFireResistantCode() {
    return fireResistantCode;
  }

  /** 接收是否阻燃编码，保持持久化映射的字段含义。 */
  public void setFireResistantCode(String value) {
    fireResistantCode = value;
  }

  /** 读取重量，单位 kg，供持久化映射使用。 */
  public BigDecimal getWeightKg() {
    return weightKg;
  }

  /** 接收重量，单位 kg，保持持久化映射的字段含义。 */
  public void setWeightKg(BigDecimal value) {
    weightKg = value;
  }

  /** 读取运输建议，供持久化映射使用。 */
  public String getTransportSuggestion() {
    return transportSuggestion;
  }

  /** 接收运输建议，保持持久化映射的字段含义。 */
  public void setTransportSuggestion(String value) {
    transportSuggestion = value;
  }

  /** 读取日租金，单位分，供持久化映射使用。 */
  public Integer getDailyRentPriceFen() {
    return dailyRentPriceFen;
  }

  /** 接收日租金，单位分，保持持久化映射的字段含义。 */
  public void setDailyRentPriceFen(Integer value) {
    dailyRentPriceFen = value;
  }

  /** 读取押金，单位分，供持久化映射使用。 */
  public Integer getDepositAmountFen() {
    return depositAmountFen;
  }

  /** 接收押金，单位分，保持持久化映射的字段含义。 */
  public void setDepositAmountFen(Integer value) {
    depositAmountFen = value;
  }

  /** 读取SKU 状态，供持久化映射使用。 */
  public String getSkuStatus() {
    return skuStatus;
  }

  /** 接收SKU 状态，保持持久化映射的字段含义。 */
  public void setSkuStatus(String value) {
    skuStatus = value;
  }

  /** 读取资料填充状态，供持久化映射使用。 */
  public String getFillStatus() {
    return fillStatus;
  }

  /** 接收资料填充状态，保持持久化映射的字段含义。 */
  public void setFillStatus(String value) {
    fillStatus = value;
  }

  /** 读取备注，供持久化映射使用。 */
  public String getRemark() {
    return remark;
  }

  /** 接收备注，保持持久化映射的字段含义。 */
  public void setRemark(String value) {
    remark = value;
  }

  /** 读取创建时间，供持久化映射使用。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持持久化映射的字段含义。 */
  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  /** 读取当前资料版本，供持久化映射使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收当前资料版本，保持持久化映射的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取更新时间，供持久化映射使用。 */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** 接收更新时间，保持持久化映射的字段含义。 */
  public void setUpdatedAt(LocalDateTime value) {
    updatedAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public PropSkuDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置所属工厂 ID并返回当前对象，供连续组装数据。 */
  public PropSkuDO factoryId(String value) {
    setFactoryId(value);
    return this;
  }

  /** 设置SKU 编码并返回当前对象，供连续组装数据。 */
  public PropSkuDO skuCode(String value) {
    setSkuCode(value);
    return this;
  }

  /** 设置道具名称并返回当前对象，供连续组装数据。 */
  public PropSkuDO propName(String value) {
    setPropName(value);
    return this;
  }

  /** 设置资产类型并返回当前对象，供连续组装数据。 */
  public PropSkuDO assetType(String value) {
    setAssetType(value);
    return this;
  }

  /** 设置主图 URL并返回当前对象，供连续组装数据。 */
  public PropSkuDO imageUrl(String value) {
    setImageUrl(value);
    return this;
  }

  /** 设置类别编码并返回当前对象，供连续组装数据。 */
  public PropSkuDO typeCode(String value) {
    setTypeCode(value);
    return this;
  }

  /** 设置尺寸描述并返回当前对象，供连续组装数据。 */
  public PropSkuDO sizeDesc(String value) {
    setSizeDesc(value);
    return this;
  }

  /** 设置长，单位 cm并返回当前对象，供连续组装数据。 */
  public PropSkuDO lengthCm(BigDecimal value) {
    setLengthCm(value);
    return this;
  }

  /** 设置宽，单位 cm并返回当前对象，供连续组装数据。 */
  public PropSkuDO widthCm(BigDecimal value) {
    setWidthCm(value);
    return this;
  }

  /** 设置高，单位 cm并返回当前对象，供连续组装数据。 */
  public PropSkuDO heightCm(BigDecimal value) {
    setHeightCm(value);
    return this;
  }

  /** 设置材质并返回当前对象，供连续组装数据。 */
  public PropSkuDO materialDesc(String value) {
    setMaterialDesc(value);
    return this;
  }

  /** 设置是否阻燃编码并返回当前对象，供连续组装数据。 */
  public PropSkuDO fireResistantCode(String value) {
    setFireResistantCode(value);
    return this;
  }

  /** 设置重量，单位 kg并返回当前对象，供连续组装数据。 */
  public PropSkuDO weightKg(BigDecimal value) {
    setWeightKg(value);
    return this;
  }

  /** 设置运输建议并返回当前对象，供连续组装数据。 */
  public PropSkuDO transportSuggestion(String value) {
    setTransportSuggestion(value);
    return this;
  }

  /** 设置日租金，单位分并返回当前对象，供连续组装数据。 */
  public PropSkuDO dailyRentPriceFen(Integer value) {
    setDailyRentPriceFen(value);
    return this;
  }

  /** 设置押金，单位分并返回当前对象，供连续组装数据。 */
  public PropSkuDO depositAmountFen(Integer value) {
    setDepositAmountFen(value);
    return this;
  }

  /** 设置SKU 状态并返回当前对象，供连续组装数据。 */
  public PropSkuDO skuStatus(String value) {
    setSkuStatus(value);
    return this;
  }

  /** 设置资料填充状态并返回当前对象，供连续组装数据。 */
  public PropSkuDO fillStatus(String value) {
    setFillStatus(value);
    return this;
  }

  /** 设置备注并返回当前对象，供连续组装数据。 */
  public PropSkuDO remark(String value) {
    setRemark(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropSkuDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置当前资料版本并返回当前对象，供连续组装数据。 */
  public PropSkuDO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public PropSkuDO updatedAt(LocalDateTime value) {
    setUpdatedAt(value);
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

  /** 链式填写包装体积系数。 */
  public PropSkuDO packagingFactorBps(Integer value) {
    packagingFactorBps = value;
    return this;
  }
}
