package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_sn_instance：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropSnInstanceDO extends BaseDO {

  /** 所属 SKU ID */
  private String skuId;

  /** 来源实物调整审核 ID */
  private String sourceAdjustmentAuditId;

  /** SN 编号 */
  private String snCode;

  /** 二维码业务编号 */
  private String qrCodeId;

  /** 实物状态 */
  private String instanceStatus;

  /** 当前关联订单 ID */
  private String currentOrderId;

  /** 状态备注 */
  private String statusRemark;

  /** 状态变更时间 */
  private LocalDateTime statusChangedAt;

  /** 状态变更操作人 */
  private String statusChangedBy;

  /** 备注 */
  private String remark;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** SN状态版本 */
  private Integer version;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 读取所属 SKU ID，供持久化映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收所属 SKU ID，保持持久化映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取来源实物调整审核 ID，供持久化映射使用。 */
  public String getSourceAdjustmentAuditId() {
    return sourceAdjustmentAuditId;
  }

  /** 接收来源实物调整审核 ID，保持持久化映射的字段含义。 */
  public void setSourceAdjustmentAuditId(String value) {
    sourceAdjustmentAuditId = value;
  }

  /** 读取SN 编号，供持久化映射使用。 */
  public String getSnCode() {
    return snCode;
  }

  /** 接收SN 编号，保持持久化映射的字段含义。 */
  public void setSnCode(String value) {
    snCode = value;
  }

  /** 读取二维码业务编号，供持久化映射使用。 */
  public String getQrCodeId() {
    return qrCodeId;
  }

  /** 接收二维码业务编号，保持持久化映射的字段含义。 */
  public void setQrCodeId(String value) {
    qrCodeId = value;
  }

  /** 读取实物状态，供持久化映射使用。 */
  public String getInstanceStatus() {
    return instanceStatus;
  }

  /** 接收实物状态，保持持久化映射的字段含义。 */
  public void setInstanceStatus(String value) {
    instanceStatus = value;
  }

  /** 读取当前关联订单 ID，供持久化映射使用。 */
  public String getCurrentOrderId() {
    return currentOrderId;
  }

  /** 接收当前关联订单 ID，保持持久化映射的字段含义。 */
  public void setCurrentOrderId(String value) {
    currentOrderId = value;
  }

  /** 读取状态备注，供持久化映射使用。 */
  public String getStatusRemark() {
    return statusRemark;
  }

  /** 接收状态备注，保持持久化映射的字段含义。 */
  public void setStatusRemark(String value) {
    statusRemark = value;
  }

  /** 读取状态变更时间，供持久化映射使用。 */
  public LocalDateTime getStatusChangedAt() {
    return statusChangedAt;
  }

  /** 接收状态变更时间，保持持久化映射的字段含义。 */
  public void setStatusChangedAt(LocalDateTime value) {
    statusChangedAt = value;
  }

  /** 读取状态变更操作人，供持久化映射使用。 */
  public String getStatusChangedBy() {
    return statusChangedBy;
  }

  /** 接收状态变更操作人，保持持久化映射的字段含义。 */
  public void setStatusChangedBy(String value) {
    statusChangedBy = value;
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

  /** 读取SN状态版本，供持久化映射使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收SN状态版本，保持持久化映射的字段含义。 */
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
  public PropSnInstanceDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置所属 SKU ID并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置来源实物调整审核 ID并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO sourceAdjustmentAuditId(String value) {
    setSourceAdjustmentAuditId(value);
    return this;
  }

  /** 设置SN 编号并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO snCode(String value) {
    setSnCode(value);
    return this;
  }

  /** 设置二维码业务编号并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO qrCodeId(String value) {
    setQrCodeId(value);
    return this;
  }

  /** 设置实物状态并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO instanceStatus(String value) {
    setInstanceStatus(value);
    return this;
  }

  /** 设置当前关联订单 ID并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO currentOrderId(String value) {
    setCurrentOrderId(value);
    return this;
  }

  /** 设置状态备注并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO statusRemark(String value) {
    setStatusRemark(value);
    return this;
  }

  /** 设置状态变更时间并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO statusChangedAt(LocalDateTime value) {
    setStatusChangedAt(value);
    return this;
  }

  /** 设置状态变更操作人并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO statusChangedBy(String value) {
    setStatusChangedBy(value);
    return this;
  }

  /** 设置备注并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO remark(String value) {
    setRemark(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置SN状态版本并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public PropSnInstanceDO updatedAt(LocalDateTime value) {
    setUpdatedAt(value);
    return this;
  }
}
