package com.mujian.backend.prop.dto;

import java.time.*;

public class ReviewQueryDTO extends PropPageFilterDTO {
  private String factory;

  private String skuId;

  private String assetType;

  private String sourceType;

  private String keyword;

  private String factoryKeyword;

  private String category;

  private String status;

  private String factoryId;
  private String reviewCategory;
  private String auditStatus;

  /** 读取公开工厂资料，供请求绑定或查询条件传递使用。 */
  public String getFactory() {
    return factory;
  }

  /** 接收公开工厂资料，保持请求绑定或查询条件传递的字段含义。 */
  public void setFactory(String value) {
    factory = value;
  }

  /** 读取道具标识，供请求绑定或查询条件传递使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持请求绑定或查询条件传递的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取资产类型，供请求绑定或查询条件传递使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持请求绑定或查询条件传递的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取业务来源类型，供请求绑定或查询条件传递使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收业务来源类型，保持请求绑定或查询条件传递的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
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

  /** 读取申请分类，供请求绑定或查询条件传递使用。 */
  public String getCategory() {
    return category;
  }

  /** 接收申请分类，保持请求绑定或查询条件传递的字段含义。 */
  public void setCategory(String value) {
    category = value;
  }

  /** 读取状态筛选条件，供请求绑定或查询条件传递使用。 */
  public String getStatus() {
    return status;
  }

  /** 接收状态筛选条件，保持请求绑定或查询条件传递的字段含义。 */
  public void setStatus(String value) {
    status = value;
  }

  /** 设置公开工厂资料并返回当前对象，供连续组装数据。 */
  public ReviewQueryDTO factory(String value) {
    setFactory(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public ReviewQueryDTO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置资产类型并返回当前对象，供连续组装数据。 */
  public ReviewQueryDTO assetType(String value) {
    setAssetType(value);
    return this;
  }

  /** 设置业务来源类型并返回当前对象，供连续组装数据。 */
  public ReviewQueryDTO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置关键词查询条件并返回当前对象，供连续组装数据。 */
  public ReviewQueryDTO keyword(String value) {
    setKeyword(value);
    return this;
  }

  /** 设置工厂关键词条件并返回当前对象，供连续组装数据。 */
  public ReviewQueryDTO factoryKeyword(String value) {
    setFactoryKeyword(value);
    return this;
  }

  /** 设置申请分类并返回当前对象，供连续组装数据。 */
  public ReviewQueryDTO category(String value) {
    setCategory(value);
    return this;
  }

  /** 设置状态筛选条件并返回当前对象，供连续组装数据。 */
  public ReviewQueryDTO status(String value) {
    setStatus(value);
    return this;
  }

  /** 读取factoryId查询条件。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 设置factoryId查询条件。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取reviewCategory查询条件。 */
  public String getReviewCategory() {
    return reviewCategory;
  }

  /** 设置reviewCategory查询条件。 */
  public void setReviewCategory(String value) {
    reviewCategory = value;
  }

  /** 读取auditStatus查询条件。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 设置auditStatus查询条件。 */
  public void setAuditStatus(String value) {
    auditStatus = value;
  }
}
