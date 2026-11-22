package com.mujian.backend.prop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** V1道具审核快照字段；JSON键保持历史snake_case。 */
public class PropSkuFieldsDTO {
  @JsonProperty("packaging_factor_bps")
  private Integer packagingFactorBps;

  @JsonProperty("prop_name")
  private String propName;

  @JsonProperty("type_code")
  private String typeCode;

  @JsonProperty("size_desc")
  private String sizeDesc;

  @JsonProperty("length_cm")
  private String lengthCm;

  @JsonProperty("width_cm")
  private String widthCm;

  @JsonProperty("height_cm")
  private String heightCm;

  @JsonProperty("material_desc")
  private String materialDesc;

  @JsonProperty("fire_resistant_code")
  private String fireResistantCode;

  @JsonProperty("weight_kg")
  private String weightKg;

  @JsonProperty("transport_suggestion")
  private String transportSuggestion;

  @JsonProperty("daily_rent_price_fen")
  private Integer dailyRentPriceFen;

  @JsonProperty("deposit_amount_fen")
  private Integer depositAmountFen;

  @JsonProperty("remark")
  private String remark;

  @JsonProperty("style_codes")
  private List<String> styleCodes;

  @JsonProperty("image_keys")
  private List<String> imageKeys;

  @JsonProperty("type_label")
  private String typeLabel;

  @JsonProperty("fire_resistant_label")
  private String fireResistantLabel;

  @JsonProperty("style_labels")
  private List<String> styleLabels;

  /** 读取道具名称，供请求绑定或查询条件传递使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持请求绑定或查询条件传递的字段含义。 */
  public void setPropName(String value) {
    propName = value;
  }

  /** 读取类别编码，供请求绑定或查询条件传递使用。 */
  public String getTypeCode() {
    return typeCode;
  }

  /** 接收类别编码，保持请求绑定或查询条件传递的字段含义。 */
  public void setTypeCode(String value) {
    typeCode = value;
  }

  /** 读取尺寸描述，供请求绑定或查询条件传递使用。 */
  public String getSizeDesc() {
    return sizeDesc;
  }

  /** 接收尺寸描述，保持请求绑定或查询条件传递的字段含义。 */
  public void setSizeDesc(String value) {
    sizeDesc = value;
  }

  /** 读取长，单位 cm，供请求绑定或查询条件传递使用。 */
  public String getLengthCm() {
    return lengthCm;
  }

  /** 接收长，单位 cm，保持请求绑定或查询条件传递的字段含义。 */
  public void setLengthCm(String value) {
    lengthCm = value;
  }

  /** 读取宽，单位 cm，供请求绑定或查询条件传递使用。 */
  public String getWidthCm() {
    return widthCm;
  }

  /** 接收宽，单位 cm，保持请求绑定或查询条件传递的字段含义。 */
  public void setWidthCm(String value) {
    widthCm = value;
  }

  /** 读取高，单位 cm，供请求绑定或查询条件传递使用。 */
  public String getHeightCm() {
    return heightCm;
  }

  /** 接收高，单位 cm，保持请求绑定或查询条件传递的字段含义。 */
  public void setHeightCm(String value) {
    heightCm = value;
  }

  /** 读取材质，供请求绑定或查询条件传递使用。 */
  public String getMaterialDesc() {
    return materialDesc;
  }

  /** 接收材质，保持请求绑定或查询条件传递的字段含义。 */
  public void setMaterialDesc(String value) {
    materialDesc = value;
  }

  /** 读取是否阻燃编码，供请求绑定或查询条件传递使用。 */
  public String getFireResistantCode() {
    return fireResistantCode;
  }

  /** 接收是否阻燃编码，保持请求绑定或查询条件传递的字段含义。 */
  public void setFireResistantCode(String value) {
    fireResistantCode = value;
  }

  /** 读取重量，单位 kg，供请求绑定或查询条件传递使用。 */
  public String getWeightKg() {
    return weightKg;
  }

  /** 接收重量，单位 kg，保持请求绑定或查询条件传递的字段含义。 */
  public void setWeightKg(String value) {
    weightKg = value;
  }

  /** 读取运输建议，供请求绑定或查询条件传递使用。 */
  public String getTransportSuggestion() {
    return transportSuggestion;
  }

  /** 接收运输建议，保持请求绑定或查询条件传递的字段含义。 */
  public void setTransportSuggestion(String value) {
    transportSuggestion = value;
  }

  /** 读取日租金，单位分，供请求绑定或查询条件传递使用。 */
  public Integer getDailyRentPriceFen() {
    return dailyRentPriceFen;
  }

  /** 接收日租金，单位分，保持请求绑定或查询条件传递的字段含义。 */
  public void setDailyRentPriceFen(Integer value) {
    dailyRentPriceFen = value;
  }

  /** 读取押金，单位分，供请求绑定或查询条件传递使用。 */
  public Integer getDepositAmountFen() {
    return depositAmountFen;
  }

  /** 接收押金，单位分，保持请求绑定或查询条件传递的字段含义。 */
  public void setDepositAmountFen(Integer value) {
    depositAmountFen = value;
  }

  /** 读取备注，供请求绑定或查询条件传递使用。 */
  public String getRemark() {
    return remark;
  }

  /** 接收备注，保持请求绑定或查询条件传递的字段含义。 */
  public void setRemark(String value) {
    remark = value;
  }

  /** 读取styleCodes，供请求绑定或查询条件传递使用。 */
  public List<String> getStyleCodes() {
    return styleCodes;
  }

  /** 接收styleCodes，保持请求绑定或查询条件传递的字段含义。 */
  public void setStyleCodes(List<String> value) {
    styleCodes = value;
  }

  /** 读取imageKeys，供请求绑定或查询条件传递使用。 */
  public List<String> getImageKeys() {
    return imageKeys;
  }

  /** 接收imageKeys，保持请求绑定或查询条件传递的字段含义。 */
  public void setImageKeys(List<String> value) {
    imageKeys = value;
  }

  /** 读取typeLabel，供请求绑定或查询条件传递使用。 */
  public String getTypeLabel() {
    return typeLabel;
  }

  /** 接收typeLabel，保持请求绑定或查询条件传递的字段含义。 */
  public void setTypeLabel(String value) {
    typeLabel = value;
  }

  /** 读取fireResistantLabel，供请求绑定或查询条件传递使用。 */
  public String getFireResistantLabel() {
    return fireResistantLabel;
  }

  /** 接收fireResistantLabel，保持请求绑定或查询条件传递的字段含义。 */
  public void setFireResistantLabel(String value) {
    fireResistantLabel = value;
  }

  /** 读取styleLabels，供请求绑定或查询条件传递使用。 */
  public List<String> getStyleLabels() {
    return styleLabels;
  }

  /** 接收styleLabels，保持请求绑定或查询条件传递的字段含义。 */
  public void setStyleLabels(List<String> value) {
    styleLabels = value;
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
