package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 QrExceptionCounts，不是数据库实体。 */
public class QrExceptionCountsVO {
  private String reported;

  private String suspectedLeak;

  private String resolvedToday;

  private String cancelledToday;

  /** 读取待处理异常数量，供接口结果展示使用。 */
  public String getReported() {
    return reported;
  }

  /** 接收待处理异常数量，保持接口结果展示的字段含义。 */
  public void setReported(String value) {
    reported = value;
  }

  /** 读取疑似泄漏异常数量，供接口结果展示使用。 */
  public String getSuspectedLeak() {
    return suspectedLeak;
  }

  /** 接收疑似泄漏异常数量，保持接口结果展示的字段含义。 */
  public void setSuspectedLeak(String value) {
    suspectedLeak = value;
  }

  /** 读取今日已处理数量，供接口结果展示使用。 */
  public String getResolvedToday() {
    return resolvedToday;
  }

  /** 接收今日已处理数量，保持接口结果展示的字段含义。 */
  public void setResolvedToday(String value) {
    resolvedToday = value;
  }

  /** 读取今日已撤销数量，供接口结果展示使用。 */
  public String getCancelledToday() {
    return cancelledToday;
  }

  /** 接收今日已撤销数量，保持接口结果展示的字段含义。 */
  public void setCancelledToday(String value) {
    cancelledToday = value;
  }

  /** 设置待处理异常数量并返回当前对象，供连续组装数据。 */
  public QrExceptionCountsVO reported(String value) {
    setReported(value);
    return this;
  }

  /** 设置疑似泄漏异常数量并返回当前对象，供连续组装数据。 */
  public QrExceptionCountsVO suspectedLeak(String value) {
    setSuspectedLeak(value);
    return this;
  }

  /** 设置今日已处理数量并返回当前对象，供连续组装数据。 */
  public QrExceptionCountsVO resolvedToday(String value) {
    setResolvedToday(value);
    return this;
  }

  /** 设置今日已撤销数量并返回当前对象，供连续组装数据。 */
  public QrExceptionCountsVO cancelledToday(String value) {
    setCancelledToday(value);
    return this;
  }
}
