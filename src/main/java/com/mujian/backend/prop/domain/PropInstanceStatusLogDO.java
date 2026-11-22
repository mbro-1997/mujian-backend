package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_instance_status_log：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropInstanceStatusLogDO extends BaseDO {

  /** SN ID */
  private String propInstanceId;

  /** SKU ID */
  private String skuId;

  /** SN 编号快照 */
  private String snCode;

  /** 二维码编号快照 */
  private String qrCodeId;

  /** 变更前状态 */
  private String fromStatus;

  /** 变更后状态 */
  private String toStatus;

  /** 变更来源类型 */
  private String sourceType;

  /** 变更原因 */
  private String reason;

  /** 操作人用户 ID */
  private String operatorUserId;

  /** 操作人角色 */
  private String operatorRole;

  /** 关联订单 ID */
  private String relatedOrderId;

  /** 关联实物调整审核 ID */
  private String relatedAdjustmentAuditId;

  /** 关联仲裁 ID */
  private String relatedArbitrationId;

  /** 效果唯一键 */
  private String businessKey;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 读取SN ID，供持久化映射使用。 */
  public String getPropInstanceId() {
    return propInstanceId;
  }

  /** 接收SN ID，保持持久化映射的字段含义。 */
  public void setPropInstanceId(String value) {
    propInstanceId = value;
  }

  /** 读取SKU ID，供持久化映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收SKU ID，保持持久化映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取SN 编号快照，供持久化映射使用。 */
  public String getSnCode() {
    return snCode;
  }

  /** 接收SN 编号快照，保持持久化映射的字段含义。 */
  public void setSnCode(String value) {
    snCode = value;
  }

  /** 读取二维码编号快照，供持久化映射使用。 */
  public String getQrCodeId() {
    return qrCodeId;
  }

  /** 接收二维码编号快照，保持持久化映射的字段含义。 */
  public void setQrCodeId(String value) {
    qrCodeId = value;
  }

  /** 读取变更前状态，供持久化映射使用。 */
  public String getFromStatus() {
    return fromStatus;
  }

  /** 接收变更前状态，保持持久化映射的字段含义。 */
  public void setFromStatus(String value) {
    fromStatus = value;
  }

  /** 读取变更后状态，供持久化映射使用。 */
  public String getToStatus() {
    return toStatus;
  }

  /** 接收变更后状态，保持持久化映射的字段含义。 */
  public void setToStatus(String value) {
    toStatus = value;
  }

  /** 读取变更来源类型，供持久化映射使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收变更来源类型，保持持久化映射的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取变更原因，供持久化映射使用。 */
  public String getReason() {
    return reason;
  }

  /** 接收变更原因，保持持久化映射的字段含义。 */
  public void setReason(String value) {
    reason = value;
  }

  /** 读取操作人用户 ID，供持久化映射使用。 */
  public String getOperatorUserId() {
    return operatorUserId;
  }

  /** 接收操作人用户 ID，保持持久化映射的字段含义。 */
  public void setOperatorUserId(String value) {
    operatorUserId = value;
  }

  /** 读取操作人角色，供持久化映射使用。 */
  public String getOperatorRole() {
    return operatorRole;
  }

  /** 接收操作人角色，保持持久化映射的字段含义。 */
  public void setOperatorRole(String value) {
    operatorRole = value;
  }

  /** 读取关联订单 ID，供持久化映射使用。 */
  public String getRelatedOrderId() {
    return relatedOrderId;
  }

  /** 接收关联订单 ID，保持持久化映射的字段含义。 */
  public void setRelatedOrderId(String value) {
    relatedOrderId = value;
  }

  /** 读取关联实物调整审核 ID，供持久化映射使用。 */
  public String getRelatedAdjustmentAuditId() {
    return relatedAdjustmentAuditId;
  }

  /** 接收关联实物调整审核 ID，保持持久化映射的字段含义。 */
  public void setRelatedAdjustmentAuditId(String value) {
    relatedAdjustmentAuditId = value;
  }

  /** 读取关联仲裁 ID，供持久化映射使用。 */
  public String getRelatedArbitrationId() {
    return relatedArbitrationId;
  }

  /** 接收关联仲裁 ID，保持持久化映射的字段含义。 */
  public void setRelatedArbitrationId(String value) {
    relatedArbitrationId = value;
  }

  /** 读取效果唯一键，供持久化映射使用。 */
  public String getBusinessKey() {
    return businessKey;
  }

  /** 接收效果唯一键，保持持久化映射的字段含义。 */
  public void setBusinessKey(String value) {
    businessKey = value;
  }

  /** 读取创建时间，供持久化映射使用。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持持久化映射的字段含义。 */
  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SN ID并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO propInstanceId(String value) {
    setPropInstanceId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置SN 编号快照并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO snCode(String value) {
    setSnCode(value);
    return this;
  }

  /** 设置二维码编号快照并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO qrCodeId(String value) {
    setQrCodeId(value);
    return this;
  }

  /** 设置变更前状态并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO fromStatus(String value) {
    setFromStatus(value);
    return this;
  }

  /** 设置变更后状态并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO toStatus(String value) {
    setToStatus(value);
    return this;
  }

  /** 设置变更来源类型并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置变更原因并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO reason(String value) {
    setReason(value);
    return this;
  }

  /** 设置操作人用户 ID并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO operatorUserId(String value) {
    setOperatorUserId(value);
    return this;
  }

  /** 设置操作人角色并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO operatorRole(String value) {
    setOperatorRole(value);
    return this;
  }

  /** 设置关联订单 ID并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO relatedOrderId(String value) {
    setRelatedOrderId(value);
    return this;
  }

  /** 设置关联实物调整审核 ID并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO relatedAdjustmentAuditId(String value) {
    setRelatedAdjustmentAuditId(value);
    return this;
  }

  /** 设置关联仲裁 ID并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO relatedArbitrationId(String value) {
    setRelatedArbitrationId(value);
    return this;
  }

  /** 设置效果唯一键并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO businessKey(String value) {
    setBusinessKey(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropInstanceStatusLogDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }
}
