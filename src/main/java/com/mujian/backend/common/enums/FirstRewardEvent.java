package com.mujian.backend.common.enums;

/** 首次事实种类与流水说明，编码固定为数据库事件名。 */
public enum FirstRewardEvent {
  first_realname_verified("首次实名奖励"),
  first_rental_paid("首次租赁付款奖励"),
  first_rental_review("首次租赁评价奖励");

  public final String description;

  FirstRewardEvent(String description) {
    this.description = description;
  }
}
