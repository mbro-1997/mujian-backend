package com.mujian.backend.prop.vo;

import java.util.List;

/** 来自D2-C接口契约 QrException，不是数据库实体。 */
public class QrExceptionVO {
  private String id;

  private String exceptionNo;

  private String factoryId;

  private String skuId;

  private String snId;

  private String originalQrId;

  private String reportedByUserId;

  private String exceptionType;

  private String description;

  private List<ImageReadVO> evidence;

  private String exceptionStatus;

  private String resolutionType;

  private String resolutionReason;

  private String resolvedAt;

  private Integer version;

  private String createdAt;

  private Boolean canApprove;

  private Boolean canReject;

  private String resolvedByAdminUserId;

  private String replacementQrId;

  private String factoryName;

  private String skuCode;

  private String propName;

  private String snCode;

  private String qrCode;

  /** 读取记录标识，供接口结果展示使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持接口结果展示的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取异常编号，供接口结果展示使用。 */
  public String getExceptionNo() {
    return exceptionNo;
  }

  /** 接收异常编号，保持接口结果展示的字段含义。 */
  public void setExceptionNo(String value) {
    exceptionNo = value;
  }

  /** 读取工厂标识，供接口结果展示使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收工厂标识，保持接口结果展示的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取道具标识，供接口结果展示使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持接口结果展示的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取SN ID，供接口结果展示使用。 */
  public String getSnId() {
    return snId;
  }

  /** 接收SN ID，保持接口结果展示的字段含义。 */
  public void setSnId(String value) {
    snId = value;
  }

  /** 读取originalQrId，供接口结果展示使用。 */
  public String getOriginalQrId() {
    return originalQrId;
  }

  /** 接收originalQrId，保持接口结果展示的字段含义。 */
  public void setOriginalQrId(String value) {
    originalQrId = value;
  }

  /** 读取报告人用户 ID，供接口结果展示使用。 */
  public String getReportedByUserId() {
    return reportedByUserId;
  }

  /** 接收报告人用户 ID，保持接口结果展示的字段含义。 */
  public void setReportedByUserId(String value) {
    reportedByUserId = value;
  }

  /** 读取异常类型，供接口结果展示使用。 */
  public String getExceptionType() {
    return exceptionType;
  }

  /** 接收异常类型，保持接口结果展示的字段含义。 */
  public void setExceptionType(String value) {
    exceptionType = value;
  }

  /** 读取异常说明，供接口结果展示使用。 */
  public String getDescription() {
    return description;
  }

  /** 接收异常说明，保持接口结果展示的字段含义。 */
  public void setDescription(String value) {
    description = value;
  }

  /** 读取evidence，供接口结果展示使用。 */
  public List<ImageReadVO> getEvidence() {
    return evidence;
  }

  /** 接收evidence，保持接口结果展示的字段含义。 */
  public void setEvidence(List<ImageReadVO> imageReadVOList) {
    evidence = imageReadVOList;
  }

  /** 读取异常状态，供接口结果展示使用。 */
  public String getExceptionStatus() {
    return exceptionStatus;
  }

  /** 接收异常状态，保持接口结果展示的字段含义。 */
  public void setExceptionStatus(String value) {
    exceptionStatus = value;
  }

  /** 读取处理类型，供接口结果展示使用。 */
  public String getResolutionType() {
    return resolutionType;
  }

  /** 接收处理类型，保持接口结果展示的字段含义。 */
  public void setResolutionType(String value) {
    resolutionType = value;
  }

  /** 读取处理理由，供接口结果展示使用。 */
  public String getResolutionReason() {
    return resolutionReason;
  }

  /** 接收处理理由，保持接口结果展示的字段含义。 */
  public void setResolutionReason(String value) {
    resolutionReason = value;
  }

  /** 读取处理时间，供接口结果展示使用。 */
  public String getResolvedAt() {
    return resolvedAt;
  }

  /** 接收处理时间，保持接口结果展示的字段含义。 */
  public void setResolvedAt(String value) {
    resolvedAt = value;
  }

  /** 读取乐观锁版本，供接口结果展示使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持接口结果展示的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取创建时间，供接口结果展示使用。 */
  public String getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持接口结果展示的字段含义。 */
  public void setCreatedAt(String value) {
    createdAt = value;
  }

  /** 读取是否允许批准，供接口结果展示使用。 */
  public Boolean getCanApprove() {
    return canApprove;
  }

  /** 接收是否允许批准，保持接口结果展示的字段含义。 */
  public void setCanApprove(Boolean value) {
    canApprove = value;
  }

  /** 读取是否允许驳回，供接口结果展示使用。 */
  public Boolean getCanReject() {
    return canReject;
  }

  /** 接收是否允许驳回，保持接口结果展示的字段含义。 */
  public void setCanReject(Boolean value) {
    canReject = value;
  }

  /** 读取处理管理员用户 ID，供接口结果展示使用。 */
  public String getResolvedByAdminUserId() {
    return resolvedByAdminUserId;
  }

  /** 接收处理管理员用户 ID，保持接口结果展示的字段含义。 */
  public void setResolvedByAdminUserId(String value) {
    resolvedByAdminUserId = value;
  }

  /** 读取replacementQrId，供接口结果展示使用。 */
  public String getReplacementQrId() {
    return replacementQrId;
  }

  /** 接收replacementQrId，保持接口结果展示的字段含义。 */
  public void setReplacementQrId(String value) {
    replacementQrId = value;
  }

  /** 读取factoryName，供接口结果展示使用。 */
  public String getFactoryName() {
    return factoryName;
  }

  /** 接收factoryName，保持接口结果展示的字段含义。 */
  public void setFactoryName(String value) {
    factoryName = value;
  }

  /** 读取SKU 编码，供接口结果展示使用。 */
  public String getSkuCode() {
    return skuCode;
  }

  /** 接收SKU 编码，保持接口结果展示的字段含义。 */
  public void setSkuCode(String value) {
    skuCode = value;
  }

  /** 读取道具名称，供接口结果展示使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持接口结果展示的字段含义。 */
  public void setPropName(String value) {
    propName = value;
  }

  /** 读取SN 编号，供接口结果展示使用。 */
  public String getSnCode() {
    return snCode;
  }

  /** 接收SN 编号，保持接口结果展示的字段含义。 */
  public void setSnCode(String value) {
    snCode = value;
  }

  /** 读取业务二维码编码，供接口结果展示使用。 */
  public String getQrCode() {
    return qrCode;
  }

  /** 接收业务二维码编码，保持接口结果展示的字段含义。 */
  public void setQrCode(String value) {
    qrCode = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public QrExceptionVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置异常编号并返回当前对象，供连续组装数据。 */
  public QrExceptionVO exceptionNo(String value) {
    setExceptionNo(value);
    return this;
  }

  /** 设置工厂标识并返回当前对象，供连续组装数据。 */
  public QrExceptionVO factoryId(String value) {
    setFactoryId(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public QrExceptionVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置SN ID并返回当前对象，供连续组装数据。 */
  public QrExceptionVO snId(String value) {
    setSnId(value);
    return this;
  }

  /** 设置originalQrId并返回当前对象，供连续组装数据。 */
  public QrExceptionVO originalQrId(String value) {
    setOriginalQrId(value);
    return this;
  }

  /** 设置报告人用户 ID并返回当前对象，供连续组装数据。 */
  public QrExceptionVO reportedByUserId(String value) {
    setReportedByUserId(value);
    return this;
  }

  /** 设置异常类型并返回当前对象，供连续组装数据。 */
  public QrExceptionVO exceptionType(String value) {
    setExceptionType(value);
    return this;
  }

  /** 设置异常说明并返回当前对象，供连续组装数据。 */
  public QrExceptionVO description(String value) {
    setDescription(value);
    return this;
  }

  /** 设置evidence并返回当前对象，供连续组装数据。 */
  public QrExceptionVO evidence(List<ImageReadVO> imageReadVOList) {
    setEvidence(imageReadVOList);
    return this;
  }

  /** 设置异常状态并返回当前对象，供连续组装数据。 */
  public QrExceptionVO exceptionStatus(String value) {
    setExceptionStatus(value);
    return this;
  }

  /** 设置处理类型并返回当前对象，供连续组装数据。 */
  public QrExceptionVO resolutionType(String value) {
    setResolutionType(value);
    return this;
  }

  /** 设置处理理由并返回当前对象，供连续组装数据。 */
  public QrExceptionVO resolutionReason(String value) {
    setResolutionReason(value);
    return this;
  }

  /** 设置处理时间并返回当前对象，供连续组装数据。 */
  public QrExceptionVO resolvedAt(String value) {
    setResolvedAt(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public QrExceptionVO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public QrExceptionVO createdAt(String value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置是否允许批准并返回当前对象，供连续组装数据。 */
  public QrExceptionVO canApprove(Boolean value) {
    setCanApprove(value);
    return this;
  }

  /** 设置是否允许驳回并返回当前对象，供连续组装数据。 */
  public QrExceptionVO canReject(Boolean value) {
    setCanReject(value);
    return this;
  }

  /** 设置处理管理员用户 ID并返回当前对象，供连续组装数据。 */
  public QrExceptionVO resolvedByAdminUserId(String value) {
    setResolvedByAdminUserId(value);
    return this;
  }

  /** 设置replacementQrId并返回当前对象，供连续组装数据。 */
  public QrExceptionVO replacementQrId(String value) {
    setReplacementQrId(value);
    return this;
  }

  /** 设置factoryName并返回当前对象，供连续组装数据。 */
  public QrExceptionVO factoryName(String value) {
    setFactoryName(value);
    return this;
  }

  /** 设置SKU 编码并返回当前对象，供连续组装数据。 */
  public QrExceptionVO skuCode(String value) {
    setSkuCode(value);
    return this;
  }

  /** 设置道具名称并返回当前对象，供连续组装数据。 */
  public QrExceptionVO propName(String value) {
    setPropName(value);
    return this;
  }

  /** 设置SN 编号并返回当前对象，供连续组装数据。 */
  public QrExceptionVO snCode(String value) {
    setSnCode(value);
    return this;
  }

  /** 设置业务二维码编码并返回当前对象，供连续组装数据。 */
  public QrExceptionVO qrCode(String value) {
    setQrCode(value);
    return this;
  }
}
