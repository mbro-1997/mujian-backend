package com.mujian.backend.common.enums;

/** 一单至多有一个有效申报版本。 */
public enum TransportExpenseStatus {
  draft,
  submitted,
  reviewing,
  approved,
  returned,
  cancelled
}
