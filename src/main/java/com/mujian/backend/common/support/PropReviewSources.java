package com.mujian.backend.common.support;

/** 道具审核的稳定来源编码；不改变来源表或现有快照值。 */
public final class PropReviewSources {
  public static final String SKU_AUDIT = "sku_audit";
  public static final String SN_ADJUSTMENT = "sn_adjustment";
  public static final String STOCK_ADJUSTMENT = "stock_adjustment";
  public static final String QR_EXCEPTION = "qr_exception";

  /** 固定编码无需实例化。 */
  private PropReviewSources() {}
}
