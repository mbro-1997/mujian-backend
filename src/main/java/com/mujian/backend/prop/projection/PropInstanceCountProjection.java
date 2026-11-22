package com.mujian.backend.prop.projection;

public class PropInstanceCountProjection {
  private String instanceStatus;

  private Integer amount;

  /** 读取实物状态，供查询结果映射使用。 */
  public String getInstanceStatus() {
    return instanceStatus;
  }

  /** 接收实物状态，保持查询结果映射的字段含义。 */
  public void setInstanceStatus(String value) {
    instanceStatus = value;
  }

  /** 读取统计数量，供查询结果映射使用。 */
  public Integer getAmount() {
    return amount;
  }

  /** 接收统计数量，保持查询结果映射的字段含义。 */
  public void setAmount(Integer value) {
    amount = value;
  }
}
