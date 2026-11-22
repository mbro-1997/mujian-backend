package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_operation_record：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropOperationRecordDO extends BaseDO {

  /** 操作人 */
  private String actorUserId;

  /** 请求编号 */
  private String requestId;

  /** 请求摘要 */
  private String requestHash;

  /** 操作类型 */
  private String operationCode;

  /** SKU */
  private String skuId;

  /** 目标类型 */
  private String targetType;

  /** 目标ID */
  private String targetId;

  /** 操作前事实 */
  private String beforeSnapshotJson;

  /** 操作后事实 */
  private String afterSnapshotJson;

  /** 原命令返回结果 */
  private String resultJson;

  /** 成功提交时间 */
  private LocalDateTime createdAt;

  /** 读取操作人，供持久化映射使用。 */
  public String getActorUserId() {
    return actorUserId;
  }

  /** 接收操作人，保持持久化映射的字段含义。 */
  public void setActorUserId(String value) {
    actorUserId = value;
  }

  /** 读取请求编号，供持久化映射使用。 */
  public String getRequestId() {
    return requestId;
  }

  /** 接收请求编号，保持持久化映射的字段含义。 */
  public void setRequestId(String value) {
    requestId = value;
  }

  /** 读取请求摘要，供持久化映射使用。 */
  public String getRequestHash() {
    return requestHash;
  }

  /** 接收请求摘要，保持持久化映射的字段含义。 */
  public void setRequestHash(String value) {
    requestHash = value;
  }

  /** 读取操作类型，供持久化映射使用。 */
  public String getOperationCode() {
    return operationCode;
  }

  /** 接收操作类型，保持持久化映射的字段含义。 */
  public void setOperationCode(String value) {
    operationCode = value;
  }

  /** 读取SKU，供持久化映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收SKU，保持持久化映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取目标类型，供持久化映射使用。 */
  public String getTargetType() {
    return targetType;
  }

  /** 接收目标类型，保持持久化映射的字段含义。 */
  public void setTargetType(String value) {
    targetType = value;
  }

  /** 读取目标ID，供持久化映射使用。 */
  public String getTargetId() {
    return targetId;
  }

  /** 接收目标ID，保持持久化映射的字段含义。 */
  public void setTargetId(String value) {
    targetId = value;
  }

  /** 读取操作前事实，供持久化映射使用。 */
  public String getBeforeSnapshotJson() {
    return beforeSnapshotJson;
  }

  /** 接收操作前事实，保持持久化映射的字段含义。 */
  public void setBeforeSnapshotJson(String value) {
    beforeSnapshotJson = value;
  }

  /** 读取操作后事实，供持久化映射使用。 */
  public String getAfterSnapshotJson() {
    return afterSnapshotJson;
  }

  /** 接收操作后事实，保持持久化映射的字段含义。 */
  public void setAfterSnapshotJson(String value) {
    afterSnapshotJson = value;
  }

  /** 读取原命令返回结果，供持久化映射使用。 */
  public String getResultJson() {
    return resultJson;
  }

  /** 接收原命令返回结果，保持持久化映射的字段含义。 */
  public void setResultJson(String value) {
    resultJson = value;
  }

  /** 读取成功提交时间，供持久化映射使用。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收成功提交时间，保持持久化映射的字段含义。 */
  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置操作人并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO actorUserId(String value) {
    setActorUserId(value);
    return this;
  }

  /** 设置请求编号并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO requestId(String value) {
    setRequestId(value);
    return this;
  }

  /** 设置请求摘要并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO requestHash(String value) {
    setRequestHash(value);
    return this;
  }

  /** 设置操作类型并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO operationCode(String value) {
    setOperationCode(value);
    return this;
  }

  /** 设置SKU并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置目标类型并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO targetType(String value) {
    setTargetType(value);
    return this;
  }

  /** 设置目标ID并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO targetId(String value) {
    setTargetId(value);
    return this;
  }

  /** 设置操作前事实并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO beforeSnapshotJson(String value) {
    setBeforeSnapshotJson(value);
    return this;
  }

  /** 设置操作后事实并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO afterSnapshotJson(String value) {
    setAfterSnapshotJson(value);
    return this;
  }

  /** 设置原命令返回结果并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO resultJson(String value) {
    setResultJson(value);
    return this;
  }

  /** 设置成功提交时间并返回当前对象，供连续组装数据。 */
  public PropOperationRecordDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }
}
