package com.mujian.backend.factory.domain;

import java.time.*;
import java.util.*;

/** 工厂资料与入驻申请共享的同义资料字段，JSON列保持既有序列化格式。 */
public class FactoryFieldsDO {
  private String factoryName;
  private String unifiedSocialCreditCode;
  private String factoryAvatarUrl;
  private String businessLicenseUrl;
  private String factoryPhotoUrls;
  private String contactName;
  private String contactPhone;
  private String factoryAddress;
  private String mainBusiness;
  private Integer workerCount;
  private String specialtyCategoryIdsJson;
  private String specialtyCategoryNamesSnapshotJson;
  private String caseMaterialsJson;
  private String remark;

  /** 读取factoryName。 */
  public String getFactoryName() {
    return factoryName;
  }

  /** 设置factoryName。 */
  public void setFactoryName(String factoryName) {
    this.factoryName = factoryName;
  }

  /** 读取unifiedSocialCreditCode。 */
  public String getUnifiedSocialCreditCode() {
    return unifiedSocialCreditCode;
  }

  /** 设置unifiedSocialCreditCode。 */
  public void setUnifiedSocialCreditCode(String unifiedSocialCreditCode) {
    this.unifiedSocialCreditCode = unifiedSocialCreditCode;
  }

  /** 读取factoryAvatarUrl。 */
  public String getFactoryAvatarUrl() {
    return factoryAvatarUrl;
  }

  /** 设置factoryAvatarUrl。 */
  public void setFactoryAvatarUrl(String factoryAvatarUrl) {
    this.factoryAvatarUrl = factoryAvatarUrl;
  }

  /** 读取businessLicenseUrl。 */
  public String getBusinessLicenseUrl() {
    return businessLicenseUrl;
  }

  /** 设置businessLicenseUrl。 */
  public void setBusinessLicenseUrl(String businessLicenseUrl) {
    this.businessLicenseUrl = businessLicenseUrl;
  }

  /** 读取factoryPhotoUrls。 */
  public String getFactoryPhotoUrls() {
    return factoryPhotoUrls;
  }

  /** 设置factoryPhotoUrls。 */
  public void setFactoryPhotoUrls(String factoryPhotoUrls) {
    this.factoryPhotoUrls = factoryPhotoUrls;
  }

  /** 读取contactName。 */
  public String getContactName() {
    return contactName;
  }

  /** 设置contactName。 */
  public void setContactName(String contactName) {
    this.contactName = contactName;
  }

  /** 读取contactPhone。 */
  public String getContactPhone() {
    return contactPhone;
  }

  /** 设置contactPhone。 */
  public void setContactPhone(String contactPhone) {
    this.contactPhone = contactPhone;
  }

  /** 读取factoryAddress。 */
  public String getFactoryAddress() {
    return factoryAddress;
  }

  /** 设置factoryAddress。 */
  public void setFactoryAddress(String factoryAddress) {
    this.factoryAddress = factoryAddress;
  }

  /** 读取mainBusiness。 */
  public String getMainBusiness() {
    return mainBusiness;
  }

  /** 设置mainBusiness。 */
  public void setMainBusiness(String mainBusiness) {
    this.mainBusiness = mainBusiness;
  }

  /** 读取workerCount。 */
  public Integer getWorkerCount() {
    return workerCount;
  }

  /** 设置workerCount。 */
  public void setWorkerCount(Integer workerCount) {
    this.workerCount = workerCount;
  }

  /** 读取specialtyCategoryIdsJson。 */
  public String getSpecialtyCategoryIdsJson() {
    return specialtyCategoryIdsJson;
  }

  /** 设置specialtyCategoryIdsJson。 */
  public void setSpecialtyCategoryIdsJson(String specialtyCategoryIdsJson) {
    this.specialtyCategoryIdsJson = specialtyCategoryIdsJson;
  }

  /** 读取specialtyCategoryNamesSnapshotJson。 */
  public String getSpecialtyCategoryNamesSnapshotJson() {
    return specialtyCategoryNamesSnapshotJson;
  }

  /** 设置specialtyCategoryNamesSnapshotJson。 */
  public void setSpecialtyCategoryNamesSnapshotJson(String specialtyCategoryNamesSnapshotJson) {
    this.specialtyCategoryNamesSnapshotJson = specialtyCategoryNamesSnapshotJson;
  }

  /** 读取caseMaterialsJson。 */
  public String getCaseMaterialsJson() {
    return caseMaterialsJson;
  }

  /** 设置caseMaterialsJson。 */
  public void setCaseMaterialsJson(String caseMaterialsJson) {
    this.caseMaterialsJson = caseMaterialsJson;
  }

  /** 读取remark。 */
  public String getRemark() {
    return remark;
  }

  /** 设置remark。 */
  public void setRemark(String remark) {
    this.remark = remark;
  }

  /** 组装factoryName。 */
  public FactoryFieldsDO factoryName(String factoryName) {
    this.factoryName = factoryName;
    return this;
  }

  /** 组装unifiedSocialCreditCode。 */
  public FactoryFieldsDO unifiedSocialCreditCode(String unifiedSocialCreditCode) {
    this.unifiedSocialCreditCode = unifiedSocialCreditCode;
    return this;
  }

  /** 组装factoryAvatarUrl。 */
  public FactoryFieldsDO factoryAvatarUrl(String factoryAvatarUrl) {
    this.factoryAvatarUrl = factoryAvatarUrl;
    return this;
  }

  /** 组装businessLicenseUrl。 */
  public FactoryFieldsDO businessLicenseUrl(String businessLicenseUrl) {
    this.businessLicenseUrl = businessLicenseUrl;
    return this;
  }

  /** 组装factoryPhotoUrls。 */
  public FactoryFieldsDO factoryPhotoUrls(String factoryPhotoUrls) {
    this.factoryPhotoUrls = factoryPhotoUrls;
    return this;
  }

  /** 组装contactName。 */
  public FactoryFieldsDO contactName(String contactName) {
    this.contactName = contactName;
    return this;
  }

  /** 组装contactPhone。 */
  public FactoryFieldsDO contactPhone(String contactPhone) {
    this.contactPhone = contactPhone;
    return this;
  }

  /** 组装factoryAddress。 */
  public FactoryFieldsDO factoryAddress(String factoryAddress) {
    this.factoryAddress = factoryAddress;
    return this;
  }

  /** 组装mainBusiness。 */
  public FactoryFieldsDO mainBusiness(String mainBusiness) {
    this.mainBusiness = mainBusiness;
    return this;
  }

  /** 组装workerCount。 */
  public FactoryFieldsDO workerCount(Integer workerCount) {
    this.workerCount = workerCount;
    return this;
  }

  /** 组装specialtyCategoryIdsJson。 */
  public FactoryFieldsDO specialtyCategoryIdsJson(String specialtyCategoryIdsJson) {
    this.specialtyCategoryIdsJson = specialtyCategoryIdsJson;
    return this;
  }

  /** 组装specialtyCategoryNamesSnapshotJson。 */
  public FactoryFieldsDO specialtyCategoryNamesSnapshotJson(
      String specialtyCategoryNamesSnapshotJson) {
    this.specialtyCategoryNamesSnapshotJson = specialtyCategoryNamesSnapshotJson;
    return this;
  }

  /** 组装caseMaterialsJson。 */
  public FactoryFieldsDO caseMaterialsJson(String caseMaterialsJson) {
    this.caseMaterialsJson = caseMaterialsJson;
    return this;
  }

  /** 组装remark。 */
  public FactoryFieldsDO remark(String remark) {
    this.remark = remark;
    return this;
  }
}
