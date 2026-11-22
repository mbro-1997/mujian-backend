package com.mujian.backend.prop.vo;

import java.util.List;

/** 来自D2-C接口契约 SnReview，不是数据库实体。 */
public class SnReviewVO extends ReviewBaseVO {
  private String adjustmentType;

  private String increaseScene;

  private Integer quantity;

  private List<AdjustmentItemVO> items;

  private String abnormalResult;

  private SnSummaryVO currentSummary;

  private String relatedSkuAuditId;

  /** 读取调整类型，供接口结果展示使用。 */
  public String getAdjustmentType() {
    return adjustmentType;
  }

  /** 接收调整类型，保持接口结果展示的字段含义。 */
  public void setAdjustmentType(String value) {
    adjustmentType = value;
  }

  /** 读取增加场景，供接口结果展示使用。 */
  public String getIncreaseScene() {
    return increaseScene;
  }

  /** 接收增加场景，保持接口结果展示的字段含义。 */
  public void setIncreaseScene(String value) {
    increaseScene = value;
  }

  /** 读取本次申请数量，供接口结果展示使用。 */
  public Integer getQuantity() {
    return quantity;
  }

  /** 接收本次申请数量，保持接口结果展示的字段含义。 */
  public void setQuantity(Integer value) {
    quantity = value;
  }

  /** 读取当前页记录，供接口结果展示使用。 */
  public List<AdjustmentItemVO> getItems() {
    return items;
  }

  /** 接收当前页记录，保持接口结果展示的字段含义。 */
  public void setItems(List<AdjustmentItemVO> adjustmentItemVOList) {
    items = adjustmentItemVOList;
  }

  /** 读取异常扣减结果，供接口结果展示使用。 */
  public String getAbnormalResult() {
    return abnormalResult;
  }

  /** 接收异常扣减结果，保持接口结果展示的字段含义。 */
  public void setAbnormalResult(String value) {
    abnormalResult = value;
  }

  /** 读取当前资产状态汇总，供接口结果展示使用。 */
  public SnSummaryVO getCurrentSummary() {
    return currentSummary;
  }

  /** 接收当前资产状态汇总，保持接口结果展示的字段含义。 */
  public void setCurrentSummary(SnSummaryVO snSummaryVO) {
    currentSummary = snSummaryVO;
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
  public SnReviewVO adjustmentType(String value) {
    setAdjustmentType(value);
    return this;
  }

  /** 设置增加场景并返回当前对象，供连续组装数据。 */
  public SnReviewVO increaseScene(String value) {
    setIncreaseScene(value);
    return this;
  }

  /** 设置本次申请数量并返回当前对象，供连续组装数据。 */
  public SnReviewVO quantity(Integer value) {
    setQuantity(value);
    return this;
  }

  /** 设置当前页记录并返回当前对象，供连续组装数据。 */
  public SnReviewVO items(List<AdjustmentItemVO> adjustmentItemVOList) {
    setItems(adjustmentItemVOList);
    return this;
  }

  /** 设置异常扣减结果并返回当前对象，供连续组装数据。 */
  public SnReviewVO abnormalResult(String value) {
    setAbnormalResult(value);
    return this;
  }

  /** 设置当前资产状态汇总并返回当前对象，供连续组装数据。 */
  public SnReviewVO currentSummary(SnSummaryVO snSummaryVO) {
    setCurrentSummary(snSummaryVO);
    return this;
  }

  /** 设置关联道具审核记录标识并返回当前对象，供连续组装数据。 */
  public SnReviewVO relatedSkuAuditId(String value) {
    setRelatedSkuAuditId(value);
    return this;
  }
}
