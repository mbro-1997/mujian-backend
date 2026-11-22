package com.mujian.backend.common.enums;

import java.time.Duration;
import java.time.LocalDateTime;

/** 04 §12 的逾期风险审核档。百分比只用于展示制度上限，实际责任另行核定。 */
public enum OverdueReviewBand {
  not_due(0),
  within_24h(0),
  within_48h(10),
  within_72h(30),
  beyond_72h(100);

  private final int ceilingPercent;

  OverdueReviewBand(int ceilingPercent) {
    this.ceilingPercent = ceilingPercent;
  }

  /** 按上界计算复核比例。 */
  public int ceilingPercent() {
    return ceilingPercent;
  }

  /** 根据输入值选择对应复核区间。 */
  public static OverdueReviewBand at(LocalDateTime due, LocalDateTime assessed) {
    return forDuration(Duration.between(due, assessed));
  }

  /** 风险展示使用观察时长，费用核定使用剔除非买家责任后的时长。 */
  public static OverdueReviewBand forDuration(Duration elapsed) {
    if (elapsed.isNegative()) return not_due;
    if (elapsed.compareTo(Duration.ofHours(24)) <= 0) return within_24h;
    if (elapsed.compareTo(Duration.ofHours(48)) <= 0) return within_48h;
    if (elapsed.compareTo(Duration.ofHours(72)) <= 0) return within_72h;
    return beyond_72h;
  }
}
