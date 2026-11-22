package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 SnSummary，不是数据库实体。 */
public class SnSummaryVO {
  private Integer total;

  private Integer pendingLabel;

  private Integer idle;

  private Integer locked;

  private Integer renting;

  private Integer repairing;

  private Integer lost;

  private Integer scrapped;

  private Integer retired;

  /** 读取匹配记录总数，供接口结果展示使用。 */
  public Integer getTotal() {
    return total;
  }

  /** 接收匹配记录总数，保持接口结果展示的字段含义。 */
  public void setTotal(Integer value) {
    total = value;
  }

  /** 读取pendingLabel，供接口结果展示使用。 */
  public Integer getPendingLabel() {
    return pendingLabel;
  }

  /** 接收pendingLabel，保持接口结果展示的字段含义。 */
  public void setPendingLabel(Integer value) {
    pendingLabel = value;
  }

  /** 读取idle，供接口结果展示使用。 */
  public Integer getIdle() {
    return idle;
  }

  /** 接收idle，保持接口结果展示的字段含义。 */
  public void setIdle(Integer value) {
    idle = value;
  }

  /** 读取locked，供接口结果展示使用。 */
  public Integer getLocked() {
    return locked;
  }

  /** 接收locked，保持接口结果展示的字段含义。 */
  public void setLocked(Integer value) {
    locked = value;
  }

  /** 读取renting，供接口结果展示使用。 */
  public Integer getRenting() {
    return renting;
  }

  /** 接收renting，保持接口结果展示的字段含义。 */
  public void setRenting(Integer value) {
    renting = value;
  }

  /** 读取repairing，供接口结果展示使用。 */
  public Integer getRepairing() {
    return repairing;
  }

  /** 接收repairing，保持接口结果展示的字段含义。 */
  public void setRepairing(Integer value) {
    repairing = value;
  }

  /** 读取lost，供接口结果展示使用。 */
  public Integer getLost() {
    return lost;
  }

  /** 接收lost，保持接口结果展示的字段含义。 */
  public void setLost(Integer value) {
    lost = value;
  }

  /** 读取scrapped，供接口结果展示使用。 */
  public Integer getScrapped() {
    return scrapped;
  }

  /** 接收scrapped，保持接口结果展示的字段含义。 */
  public void setScrapped(Integer value) {
    scrapped = value;
  }

  /** 读取retired，供接口结果展示使用。 */
  public Integer getRetired() {
    return retired;
  }

  /** 接收retired，保持接口结果展示的字段含义。 */
  public void setRetired(Integer value) {
    retired = value;
  }

  /** 设置匹配记录总数并返回当前对象，供连续组装数据。 */
  public SnSummaryVO total(Integer value) {
    setTotal(value);
    return this;
  }

  /** 设置pendingLabel并返回当前对象，供连续组装数据。 */
  public SnSummaryVO pendingLabel(Integer value) {
    setPendingLabel(value);
    return this;
  }

  /** 设置idle并返回当前对象，供连续组装数据。 */
  public SnSummaryVO idle(Integer value) {
    setIdle(value);
    return this;
  }

  /** 设置locked并返回当前对象，供连续组装数据。 */
  public SnSummaryVO locked(Integer value) {
    setLocked(value);
    return this;
  }

  /** 设置renting并返回当前对象，供连续组装数据。 */
  public SnSummaryVO renting(Integer value) {
    setRenting(value);
    return this;
  }

  /** 设置repairing并返回当前对象，供连续组装数据。 */
  public SnSummaryVO repairing(Integer value) {
    setRepairing(value);
    return this;
  }

  /** 设置lost并返回当前对象，供连续组装数据。 */
  public SnSummaryVO lost(Integer value) {
    setLost(value);
    return this;
  }

  /** 设置scrapped并返回当前对象，供连续组装数据。 */
  public SnSummaryVO scrapped(Integer value) {
    setScrapped(value);
    return this;
  }

  /** 设置retired并返回当前对象，供连续组装数据。 */
  public SnSummaryVO retired(Integer value) {
    setRetired(value);
    return this;
  }
}
