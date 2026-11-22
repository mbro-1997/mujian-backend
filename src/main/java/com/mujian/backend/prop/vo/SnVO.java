package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 Sn，不是数据库实体。 */
public class SnVO {
  private String id;

  private String snCode;

  private String skuId;

  private String instanceStatus;

  private Integer version;

  private String qrCodeId;

  private String currentQrId;

  private String pendingAdjustmentId;

  private Boolean canSelectForDecrease;

  private Boolean canSelectForRepair;

  private Boolean canSelectForAbnormal;

  private Boolean canCompleteRepair;

  private Boolean canConfirmLabel;

  private String currentOrderId;

  private String statusRemark;

  private String updatedAt;

  private String latestQrId;

  private String statusChangedAt;

  private String remark;

  /** 读取记录标识，供接口结果展示使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持接口结果展示的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取SN 编号，供接口结果展示使用。 */
  public String getSnCode() {
    return snCode;
  }

  /** 接收SN 编号，保持接口结果展示的字段含义。 */
  public void setSnCode(String value) {
    snCode = value;
  }

  /** 读取道具标识，供接口结果展示使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持接口结果展示的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取实物状态，供接口结果展示使用。 */
  public String getInstanceStatus() {
    return instanceStatus;
  }

  /** 接收实物状态，保持接口结果展示的字段含义。 */
  public void setInstanceStatus(String value) {
    instanceStatus = value;
  }

  /** 读取乐观锁版本，供接口结果展示使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持接口结果展示的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取二维码业务编号，供接口结果展示使用。 */
  public String getQrCodeId() {
    return qrCodeId;
  }

  /** 接收二维码业务编号，保持接口结果展示的字段含义。 */
  public void setQrCodeId(String value) {
    qrCodeId = value;
  }

  /** 读取当前有效二维码记录标识，供接口结果展示使用。 */
  public String getCurrentQrId() {
    return currentQrId;
  }

  /** 接收当前有效二维码记录标识，保持接口结果展示的字段含义。 */
  public void setCurrentQrId(String value) {
    currentQrId = value;
  }

  /** 读取pendingAdjustmentId，供接口结果展示使用。 */
  public String getPendingAdjustmentId() {
    return pendingAdjustmentId;
  }

  /** 接收pendingAdjustmentId，保持接口结果展示的字段含义。 */
  public void setPendingAdjustmentId(String value) {
    pendingAdjustmentId = value;
  }

  /** 读取是否允许选中退役，供接口结果展示使用。 */
  public Boolean getCanSelectForDecrease() {
    return canSelectForDecrease;
  }

  /** 接收是否允许选中退役，保持接口结果展示的字段含义。 */
  public void setCanSelectForDecrease(Boolean value) {
    canSelectForDecrease = value;
  }

  /** 读取是否允许选中送修，供接口结果展示使用。 */
  public Boolean getCanSelectForRepair() {
    return canSelectForRepair;
  }

  /** 接收是否允许选中送修，保持接口结果展示的字段含义。 */
  public void setCanSelectForRepair(Boolean value) {
    canSelectForRepair = value;
  }

  /** 读取是否允许选中异常扣减，供接口结果展示使用。 */
  public Boolean getCanSelectForAbnormal() {
    return canSelectForAbnormal;
  }

  /** 接收是否允许选中异常扣减，保持接口结果展示的字段含义。 */
  public void setCanSelectForAbnormal(Boolean value) {
    canSelectForAbnormal = value;
  }

  /** 读取是否允许确认维修完成，供接口结果展示使用。 */
  public Boolean getCanCompleteRepair() {
    return canCompleteRepair;
  }

  /** 接收是否允许确认维修完成，保持接口结果展示的字段含义。 */
  public void setCanCompleteRepair(Boolean value) {
    canCompleteRepair = value;
  }

  /** 读取是否允许确认贴标，供接口结果展示使用。 */
  public Boolean getCanConfirmLabel() {
    return canConfirmLabel;
  }

  /** 接收是否允许确认贴标，保持接口结果展示的字段含义。 */
  public void setCanConfirmLabel(Boolean value) {
    canConfirmLabel = value;
  }

  /** 读取当前关联订单 ID，供接口结果展示使用。 */
  public String getCurrentOrderId() {
    return currentOrderId;
  }

  /** 接收当前关联订单 ID，保持接口结果展示的字段含义。 */
  public void setCurrentOrderId(String value) {
    currentOrderId = value;
  }

  /** 读取状态备注，供接口结果展示使用。 */
  public String getStatusRemark() {
    return statusRemark;
  }

  /** 接收状态备注，保持接口结果展示的字段含义。 */
  public void setStatusRemark(String value) {
    statusRemark = value;
  }

  /** 读取更新时间，供接口结果展示使用。 */
  public String getUpdatedAt() {
    return updatedAt;
  }

  /** 接收更新时间，保持接口结果展示的字段含义。 */
  public void setUpdatedAt(String value) {
    updatedAt = value;
  }

  /** 读取最新二维码记录标识，供接口结果展示使用。 */
  public String getLatestQrId() {
    return latestQrId;
  }

  /** 接收最新二维码记录标识，保持接口结果展示的字段含义。 */
  public void setLatestQrId(String value) {
    latestQrId = value;
  }

  /** 读取状态变更时间，供接口结果展示使用。 */
  public String getStatusChangedAt() {
    return statusChangedAt;
  }

  /** 接收状态变更时间，保持接口结果展示的字段含义。 */
  public void setStatusChangedAt(String value) {
    statusChangedAt = value;
  }

  /** 读取备注，供接口结果展示使用。 */
  public String getRemark() {
    return remark;
  }

  /** 接收备注，保持接口结果展示的字段含义。 */
  public void setRemark(String value) {
    remark = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public SnVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SN 编号并返回当前对象，供连续组装数据。 */
  public SnVO snCode(String value) {
    setSnCode(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public SnVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置实物状态并返回当前对象，供连续组装数据。 */
  public SnVO instanceStatus(String value) {
    setInstanceStatus(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public SnVO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置二维码业务编号并返回当前对象，供连续组装数据。 */
  public SnVO qrCodeId(String value) {
    setQrCodeId(value);
    return this;
  }

  /** 设置当前有效二维码记录标识并返回当前对象，供连续组装数据。 */
  public SnVO currentQrId(String value) {
    setCurrentQrId(value);
    return this;
  }

  /** 设置pendingAdjustmentId并返回当前对象，供连续组装数据。 */
  public SnVO pendingAdjustmentId(String value) {
    setPendingAdjustmentId(value);
    return this;
  }

  /** 设置是否允许选中退役并返回当前对象，供连续组装数据。 */
  public SnVO canSelectForDecrease(Boolean value) {
    setCanSelectForDecrease(value);
    return this;
  }

  /** 设置是否允许选中送修并返回当前对象，供连续组装数据。 */
  public SnVO canSelectForRepair(Boolean value) {
    setCanSelectForRepair(value);
    return this;
  }

  /** 设置是否允许选中异常扣减并返回当前对象，供连续组装数据。 */
  public SnVO canSelectForAbnormal(Boolean value) {
    setCanSelectForAbnormal(value);
    return this;
  }

  /** 设置是否允许确认维修完成并返回当前对象，供连续组装数据。 */
  public SnVO canCompleteRepair(Boolean value) {
    setCanCompleteRepair(value);
    return this;
  }

  /** 设置是否允许确认贴标并返回当前对象，供连续组装数据。 */
  public SnVO canConfirmLabel(Boolean value) {
    setCanConfirmLabel(value);
    return this;
  }

  /** 设置当前关联订单 ID并返回当前对象，供连续组装数据。 */
  public SnVO currentOrderId(String value) {
    setCurrentOrderId(value);
    return this;
  }

  /** 设置状态备注并返回当前对象，供连续组装数据。 */
  public SnVO statusRemark(String value) {
    setStatusRemark(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public SnVO updatedAt(String value) {
    setUpdatedAt(value);
    return this;
  }

  /** 设置最新二维码记录标识并返回当前对象，供连续组装数据。 */
  public SnVO latestQrId(String value) {
    setLatestQrId(value);
    return this;
  }

  /** 设置状态变更时间并返回当前对象，供连续组装数据。 */
  public SnVO statusChangedAt(String value) {
    setStatusChangedAt(value);
    return this;
  }

  /** 设置备注并返回当前对象，供连续组装数据。 */
  public SnVO remark(String value) {
    setRemark(value);
    return this;
  }
}
