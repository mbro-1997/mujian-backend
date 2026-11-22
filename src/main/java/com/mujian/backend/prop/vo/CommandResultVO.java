package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 CommandResult，不是数据库实体。 */
public class CommandResultVO {
  private String operation;

  private String outcome;

  private String skuId;

  private String targetType;

  private String targetId;

  private Integer version;

  private String status;

  private String completedAt;

  /** 读取业务操作类型，供接口结果展示使用。 */
  public String getOperation() {
    return operation;
  }

  /** 接收业务操作类型，保持接口结果展示的字段含义。 */
  public void setOperation(String value) {
    operation = value;
  }

  /** 读取本次操作结果，供接口结果展示使用。 */
  public String getOutcome() {
    return outcome;
  }

  /** 接收本次操作结果，保持接口结果展示的字段含义。 */
  public void setOutcome(String value) {
    outcome = value;
  }

  /** 读取道具标识，供接口结果展示使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持接口结果展示的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取关联目标类型，供接口结果展示使用。 */
  public String getTargetType() {
    return targetType;
  }

  /** 接收关联目标类型，保持接口结果展示的字段含义。 */
  public void setTargetType(String value) {
    targetType = value;
  }

  /** 读取关联目标标识，供接口结果展示使用。 */
  public String getTargetId() {
    return targetId;
  }

  /** 接收关联目标标识，保持接口结果展示的字段含义。 */
  public void setTargetId(String value) {
    targetId = value;
  }

  /** 读取乐观锁版本，供接口结果展示使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持接口结果展示的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取状态筛选条件，供接口结果展示使用。 */
  public String getStatus() {
    return status;
  }

  /** 接收状态筛选条件，保持接口结果展示的字段含义。 */
  public void setStatus(String value) {
    status = value;
  }

  /** 读取completedAt，供接口结果展示使用。 */
  public String getCompletedAt() {
    return completedAt;
  }

  /** 接收completedAt，保持接口结果展示的字段含义。 */
  public void setCompletedAt(String value) {
    completedAt = value;
  }

  /** 设置业务操作类型并返回当前对象，供连续组装数据。 */
  public CommandResultVO operation(String value) {
    setOperation(value);
    return this;
  }

  /** 设置本次操作结果并返回当前对象，供连续组装数据。 */
  public CommandResultVO outcome(String value) {
    setOutcome(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public CommandResultVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置关联目标类型并返回当前对象，供连续组装数据。 */
  public CommandResultVO targetType(String value) {
    setTargetType(value);
    return this;
  }

  /** 设置关联目标标识并返回当前对象，供连续组装数据。 */
  public CommandResultVO targetId(String value) {
    setTargetId(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public CommandResultVO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置状态筛选条件并返回当前对象，供连续组装数据。 */
  public CommandResultVO status(String value) {
    setStatus(value);
    return this;
  }

  /** 设置completedAt并返回当前对象，供连续组装数据。 */
  public CommandResultVO completedAt(String value) {
    setCompletedAt(value);
    return this;
  }
}
