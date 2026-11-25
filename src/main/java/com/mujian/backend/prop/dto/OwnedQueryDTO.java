package com.mujian.backend.prop.dto;

import java.time.*;
import java.util.List;

public class OwnedQueryDTO extends PropPageFilterDTO {
  private String ownerFactory;

  private String typeCode;

  private String assetType;

  private String factoryId;

  private String skuStatus;

  private String keyword;

  private String factoryKeyword;

  private List<String> styleCodes;

  private boolean admin;

  private Boolean auditing;

  private boolean hasPendingLabel;

  /** 是否只查存在待贴标实物的道具。 */
  public boolean isHasPendingLabel() {
    return hasPendingLabel;
  }

  public void setHasPendingLabel(boolean value) {
    hasPendingLabel = value;
  }

  /** 读取当前工厂数据范围，供请求绑定或查询条件传递使用。 */
  public String getOwnerFactory() {
    return ownerFactory;
  }

  /** 接收当前工厂数据范围，保持请求绑定或查询条件传递的字段含义。 */
  public void setOwnerFactory(String value) {
    ownerFactory = value;
  }

  /** 读取类别编码，供请求绑定或查询条件传递使用。 */
  public String getTypeCode() {
    return typeCode;
  }

  /** 接收类别编码，保持请求绑定或查询条件传递的字段含义。 */
  public void setTypeCode(String value) {
    typeCode = value;
  }

  /** 读取资产类型，供请求绑定或查询条件传递使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持请求绑定或查询条件传递的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取工厂标识，供请求绑定或查询条件传递使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收工厂标识，保持请求绑定或查询条件传递的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取道具上下架状态，供请求绑定或查询条件传递使用。 */
  public String getSkuStatus() {
    return skuStatus;
  }

  /** 接收道具上下架状态，保持请求绑定或查询条件传递的字段含义。 */
  public void setSkuStatus(String value) {
    skuStatus = value;
  }

  /** 读取关键词查询条件，供请求绑定或查询条件传递使用。 */
  public String getKeyword() {
    return keyword;
  }

  /** 接收关键词查询条件，保持请求绑定或查询条件传递的字段含义。 */
  public void setKeyword(String value) {
    keyword = value;
  }

  /** 读取工厂关键词条件，供请求绑定或查询条件传递使用。 */
  public String getFactoryKeyword() {
    return factoryKeyword;
  }

  /** 接收工厂关键词条件，保持请求绑定或查询条件传递的字段含义。 */
  public void setFactoryKeyword(String value) {
    factoryKeyword = value;
  }

  /** 读取有序风格集合，供请求绑定或查询条件传递使用。 */
  public List<String> getStyleCodes() {
    return styleCodes;
  }

  /** 接收有序风格集合，保持请求绑定或查询条件传递的字段含义。 */
  public void setStyleCodes(List<String> value) {
    styleCodes = com.mujian.backend.prop.policy.PropStyleFilter.validate(value);
  }

  /** 读取是否使用管理端查询范围，供请求绑定或查询条件传递使用。 */
  public boolean getAdmin() {
    return admin;
  }

  /** 接收是否使用管理端查询范围，保持请求绑定或查询条件传递的字段含义。 */
  public void setAdmin(boolean value) {
    admin = value;
  }

  /** 读取是否只查询审核中道具，供请求绑定或查询条件传递使用。 */
  public Boolean getAuditing() {
    return auditing;
  }

  /** 接收是否只查询审核中道具，保持请求绑定或查询条件传递的字段含义。 */
  public void setAuditing(Boolean value) {
    auditing = value;
  }

  /** 设置当前工厂数据范围并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO ownerFactory(String value) {
    setOwnerFactory(value);
    return this;
  }

  /** 设置类别编码并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO typeCode(String value) {
    setTypeCode(value);
    return this;
  }

  /** 设置资产类型并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO assetType(String value) {
    setAssetType(value);
    return this;
  }

  /** 设置工厂标识并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO factoryId(String value) {
    setFactoryId(value);
    return this;
  }

  /** 设置道具上下架状态并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO skuStatus(String value) {
    setSkuStatus(value);
    return this;
  }

  /** 设置关键词查询条件并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO keyword(String value) {
    setKeyword(value);
    return this;
  }

  /** 设置工厂关键词条件并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO factoryKeyword(String value) {
    setFactoryKeyword(value);
    return this;
  }

  /** 设置有序风格集合并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO styleCodes(List<String> value) {
    setStyleCodes(value);
    return this;
  }

  /** 设置是否使用管理端查询范围并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO admin(boolean value) {
    setAdmin(value);
    return this;
  }

  /** 设置是否只查询审核中道具并返回当前对象，供连续组装数据。 */
  public OwnedQueryDTO auditing(Boolean value) {
    setAuditing(value);
    return this;
  }
}
