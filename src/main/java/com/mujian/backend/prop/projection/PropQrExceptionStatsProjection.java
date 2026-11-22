package com.mujian.backend.prop.projection;

public class PropQrExceptionStatsProjection {
  private Integer reported;

  private Integer suspectedLeak;

  private Integer resolvedToday;

  private Integer cancelledToday;

  /** 读取待处理异常数量，供查询结果映射使用。 */
  public Integer getReported() {
    return reported;
  }

  /** 接收待处理异常数量，保持查询结果映射的字段含义。 */
  public void setReported(Integer value) {
    reported = value;
  }

  /** 读取疑似泄漏异常数量，供查询结果映射使用。 */
  public Integer getSuspectedLeak() {
    return suspectedLeak;
  }

  /** 接收疑似泄漏异常数量，保持查询结果映射的字段含义。 */
  public void setSuspectedLeak(Integer value) {
    suspectedLeak = value;
  }

  /** 读取今日已处理数量，供查询结果映射使用。 */
  public Integer getResolvedToday() {
    return resolvedToday;
  }

  /** 接收今日已处理数量，保持查询结果映射的字段含义。 */
  public void setResolvedToday(Integer value) {
    resolvedToday = value;
  }

  /** 读取今日已撤销数量，供查询结果映射使用。 */
  public Integer getCancelledToday() {
    return cancelledToday;
  }

  /** 接收今日已撤销数量，保持查询结果映射的字段含义。 */
  public void setCancelledToday(Integer value) {
    cancelledToday = value;
  }
}
