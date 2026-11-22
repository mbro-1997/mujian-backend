package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_qr_exception_record：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropQrExceptionRecordDO extends BaseDO {

  /** 异常编号 */
  private String exceptionNo;

  /** 工厂 ID */
  private String factoryId;

  /** SKU ID */
  private String skuId;

  /** SN ID */
  private String snId;

  /** 原二维码记录 ID */
  private String originalQrCodeId;

  /** 报告人用户 ID */
  private String reportedByUserId;

  /** 异常类型 */
  private String exceptionType;

  /** 异常说明 */
  private String description;

  /** 证据对象键 */
  private String evidenceUrlsJson;

  /** 异常状态 */
  private String exceptionStatus;

  /** 活动异常原码守卫 */
  private String activeOriginalQrGuard;

  /** 处理类型 */
  private String resolutionType;

  /** 补发二维码 ID */
  private String replacementQrCodeId;

  /** 处理管理员用户 ID */
  private String resolvedByAdminUserId;

  /** 处理理由 */
  private String resolutionReason;

  /** 处理时间 */
  private LocalDateTime resolvedAt;

  /** 乐观锁版本 */
  private Integer version;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 登记请求编号 */
  private String requestId;

  /** 登记摘要 */
  private String requestHash;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 读取异常编号，供持久化映射使用。 */
  public String getExceptionNo() {
    return exceptionNo;
  }

  /** 接收异常编号，保持持久化映射的字段含义。 */
  public void setExceptionNo(String value) {
    exceptionNo = value;
  }

  /** 读取工厂 ID，供持久化映射使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收工厂 ID，保持持久化映射的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取SKU ID，供持久化映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收SKU ID，保持持久化映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取SN ID，供持久化映射使用。 */
  public String getSnId() {
    return snId;
  }

  /** 接收SN ID，保持持久化映射的字段含义。 */
  public void setSnId(String value) {
    snId = value;
  }

  /** 读取原二维码记录 ID，供持久化映射使用。 */
  public String getOriginalQrCodeId() {
    return originalQrCodeId;
  }

  /** 接收原二维码记录 ID，保持持久化映射的字段含义。 */
  public void setOriginalQrCodeId(String value) {
    originalQrCodeId = value;
  }

  /** 读取报告人用户 ID，供持久化映射使用。 */
  public String getReportedByUserId() {
    return reportedByUserId;
  }

  /** 接收报告人用户 ID，保持持久化映射的字段含义。 */
  public void setReportedByUserId(String value) {
    reportedByUserId = value;
  }

  /** 读取异常类型，供持久化映射使用。 */
  public String getExceptionType() {
    return exceptionType;
  }

  /** 接收异常类型，保持持久化映射的字段含义。 */
  public void setExceptionType(String value) {
    exceptionType = value;
  }

  /** 读取异常说明，供持久化映射使用。 */
  public String getDescription() {
    return description;
  }

  /** 接收异常说明，保持持久化映射的字段含义。 */
  public void setDescription(String value) {
    description = value;
  }

  /** 读取证据对象键，供持久化映射使用。 */
  public String getEvidenceUrlsJson() {
    return evidenceUrlsJson;
  }

  /** 接收证据对象键，保持持久化映射的字段含义。 */
  public void setEvidenceUrlsJson(String value) {
    evidenceUrlsJson = value;
  }

  /** 读取异常状态，供持久化映射使用。 */
  public String getExceptionStatus() {
    return exceptionStatus;
  }

  /** 接收异常状态，保持持久化映射的字段含义。 */
  public void setExceptionStatus(String value) {
    exceptionStatus = value;
  }

  /** 读取活动异常原码守卫，供持久化映射使用。 */
  public String getActiveOriginalQrGuard() {
    return activeOriginalQrGuard;
  }

  /** 接收活动异常原码守卫，保持持久化映射的字段含义。 */
  public void setActiveOriginalQrGuard(String value) {
    activeOriginalQrGuard = value;
  }

  /** 读取处理类型，供持久化映射使用。 */
  public String getResolutionType() {
    return resolutionType;
  }

  /** 接收处理类型，保持持久化映射的字段含义。 */
  public void setResolutionType(String value) {
    resolutionType = value;
  }

  /** 读取补发二维码 ID，供持久化映射使用。 */
  public String getReplacementQrCodeId() {
    return replacementQrCodeId;
  }

  /** 接收补发二维码 ID，保持持久化映射的字段含义。 */
  public void setReplacementQrCodeId(String value) {
    replacementQrCodeId = value;
  }

  /** 读取处理管理员用户 ID，供持久化映射使用。 */
  public String getResolvedByAdminUserId() {
    return resolvedByAdminUserId;
  }

  /** 接收处理管理员用户 ID，保持持久化映射的字段含义。 */
  public void setResolvedByAdminUserId(String value) {
    resolvedByAdminUserId = value;
  }

  /** 读取处理理由，供持久化映射使用。 */
  public String getResolutionReason() {
    return resolutionReason;
  }

  /** 接收处理理由，保持持久化映射的字段含义。 */
  public void setResolutionReason(String value) {
    resolutionReason = value;
  }

  /** 读取处理时间，供持久化映射使用。 */
  public LocalDateTime getResolvedAt() {
    return resolvedAt;
  }

  /** 接收处理时间，保持持久化映射的字段含义。 */
  public void setResolvedAt(LocalDateTime value) {
    resolvedAt = value;
  }

  /** 读取乐观锁版本，供持久化映射使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持持久化映射的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取创建时间，供持久化映射使用。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持持久化映射的字段含义。 */
  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  /** 读取登记请求编号，供持久化映射使用。 */
  public String getRequestId() {
    return requestId;
  }

  /** 接收登记请求编号，保持持久化映射的字段含义。 */
  public void setRequestId(String value) {
    requestId = value;
  }

  /** 读取登记摘要，供持久化映射使用。 */
  public String getRequestHash() {
    return requestHash;
  }

  /** 接收登记摘要，保持持久化映射的字段含义。 */
  public void setRequestHash(String value) {
    requestHash = value;
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
  public PropQrExceptionRecordDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置异常编号并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO exceptionNo(String value) {
    setExceptionNo(value);
    return this;
  }

  /** 设置工厂 ID并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO factoryId(String value) {
    setFactoryId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置SN ID并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO snId(String value) {
    setSnId(value);
    return this;
  }

  /** 设置原二维码记录 ID并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO originalQrCodeId(String value) {
    setOriginalQrCodeId(value);
    return this;
  }

  /** 设置报告人用户 ID并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO reportedByUserId(String value) {
    setReportedByUserId(value);
    return this;
  }

  /** 设置异常类型并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO exceptionType(String value) {
    setExceptionType(value);
    return this;
  }

  /** 设置异常说明并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO description(String value) {
    setDescription(value);
    return this;
  }

  /** 设置证据对象键并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO evidenceUrlsJson(String value) {
    setEvidenceUrlsJson(value);
    return this;
  }

  /** 设置异常状态并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO exceptionStatus(String value) {
    setExceptionStatus(value);
    return this;
  }

  /** 设置活动异常原码守卫并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO activeOriginalQrGuard(String value) {
    setActiveOriginalQrGuard(value);
    return this;
  }

  /** 设置处理类型并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO resolutionType(String value) {
    setResolutionType(value);
    return this;
  }

  /** 设置补发二维码 ID并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO replacementQrCodeId(String value) {
    setReplacementQrCodeId(value);
    return this;
  }

  /** 设置处理管理员用户 ID并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO resolvedByAdminUserId(String value) {
    setResolvedByAdminUserId(value);
    return this;
  }

  /** 设置处理理由并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO resolutionReason(String value) {
    setResolutionReason(value);
    return this;
  }

  /** 设置处理时间并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO resolvedAt(LocalDateTime value) {
    setResolvedAt(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置登记请求编号并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO requestId(String value) {
    setRequestId(value);
    return this;
  }

  /** 设置登记摘要并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO requestHash(String value) {
    setRequestHash(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public PropQrExceptionRecordDO updatedAt(LocalDateTime value) {
    setUpdatedAt(value);
    return this;
  }
}
