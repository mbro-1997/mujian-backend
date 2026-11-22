package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 StockReview，不是数据库实体。 */
public class StockReviewVO extends ReviewBaseVO {
  private String adjustmentType;

  private Integer quantityDelta;

  private String abnormalResult;

  private Integer beforeTotalQuantity;

  private Integer beforeAvailableQuantity;

  private StockVO currentStock;

  private String relatedSkuAuditId;

  /** 读取调整类型，供接口结果展示使用。 */
  public String getAdjustmentType() {
    return adjustmentType;
  }

  /** 接收调整类型，保持接口结果展示的字段含义。 */
  public void setAdjustmentType(String value) {
    adjustmentType = value;
  }

  /** 读取变化数量，供接口结果展示使用。 */
  public Integer getQuantityDelta() {
    return quantityDelta;
  }

  /** 接收变化数量，保持接口结果展示的字段含义。 */
  public void setQuantityDelta(Integer value) {
    quantityDelta = value;
  }

  /** 读取异常扣减结果，供接口结果展示使用。 */
  public String getAbnormalResult() {
    return abnormalResult;
  }

  /** 接收异常扣减结果，保持接口结果展示的字段含义。 */
  public void setAbnormalResult(String value) {
    abnormalResult = value;
  }

  /** 读取调整前总数量，供接口结果展示使用。 */
  public Integer getBeforeTotalQuantity() {
    return beforeTotalQuantity;
  }

  /** 接收调整前总数量，保持接口结果展示的字段含义。 */
  public void setBeforeTotalQuantity(Integer value) {
    beforeTotalQuantity = value;
  }

  /** 读取调整前可用数量，供接口结果展示使用。 */
  public Integer getBeforeAvailableQuantity() {
    return beforeAvailableQuantity;
  }

  /** 接收调整前可用数量，保持接口结果展示的字段含义。 */
  public void setBeforeAvailableQuantity(Integer value) {
    beforeAvailableQuantity = value;
  }

  /** 读取当前库存台账，供接口结果展示使用。 */
  public StockVO getCurrentStock() {
    return currentStock;
  }

  /** 接收当前库存台账，保持接口结果展示的字段含义。 */
  public void setCurrentStock(StockVO stockVO) {
    currentStock = stockVO;
  }

  /** 读取关联道具审核记录标识，供接口结果展示使用。 */
  public String getRelatedSkuAuditId() {
    return relatedSkuAuditId;
  }

  /** 接收关联道具审核记录标识，保持接口结果展示的字段含义。 */
  public void setRelatedSkuAuditId(String value) {
    relatedSkuAuditId = value;
  }

  /** 设置调整类型并返回当前对象，供连续组装数据。 */
  public StockReviewVO adjustmentType(String value) {
    setAdjustmentType(value);
    return this;
  }

  /** 设置变化数量并返回当前对象，供连续组装数据。 */
  public StockReviewVO quantityDelta(Integer value) {
    setQuantityDelta(value);
    return this;
  }

  /** 设置异常扣减结果并返回当前对象，供连续组装数据。 */
  public StockReviewVO abnormalResult(String value) {
    setAbnormalResult(value);
    return this;
  }

  /** 设置调整前总数量并返回当前对象，供连续组装数据。 */
  public StockReviewVO beforeTotalQuantity(Integer value) {
    setBeforeTotalQuantity(value);
    return this;
  }

  /** 设置调整前可用数量并返回当前对象，供连续组装数据。 */
  public StockReviewVO beforeAvailableQuantity(Integer value) {
    setBeforeAvailableQuantity(value);
    return this;
  }

  /** 设置当前库存台账并返回当前对象，供连续组装数据。 */
  public StockReviewVO currentStock(StockVO stockVO) {
    setCurrentStock(stockVO);
    return this;
  }

  /** 设置关联道具审核记录标识并返回当前对象，供连续组装数据。 */
  public StockReviewVO relatedSkuAuditId(String value) {
    setRelatedSkuAuditId(value);
    return this;
  }
}
