package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 SkuReview，不是数据库实体。 */
public class SkuReviewVO extends ReviewBaseVO {
  private String actionType;

  private String beforeSkuStatus;

  private String targetSkuStatus;

  private SkuReadVO before;

  private SkuReadVO proposed;

  private SkuReadVO current;

  private Integer initialQuantity;

  private Integer baseSkuVersion;

  private String previousApplicationId;

  private String currentFillStatus;

  private String currentUpdatedAt;

  /** 读取申请动作，供接口结果展示使用。 */
  public String getActionType() {
    return actionType;
  }

  /** 接收申请动作，保持接口结果展示的字段含义。 */
  public void setActionType(String value) {
    actionType = value;
  }

  /** 读取申请前 SKU 状态，供接口结果展示使用。 */
  public String getBeforeSkuStatus() {
    return beforeSkuStatus;
  }

  /** 接收申请前 SKU 状态，保持接口结果展示的字段含义。 */
  public void setBeforeSkuStatus(String value) {
    beforeSkuStatus = value;
  }

  /** 读取审核通过后的目标 SKU 状态，供接口结果展示使用。 */
  public String getTargetSkuStatus() {
    return targetSkuStatus;
  }

  /** 接收审核通过后的目标 SKU 状态，保持接口结果展示的字段含义。 */
  public void setTargetSkuStatus(String value) {
    targetSkuStatus = value;
  }

  /** 读取变更前快照，供接口结果展示使用。 */
  public SkuReadVO getBefore() {
    return before;
  }

  /** 接收变更前快照，保持接口结果展示的字段含义。 */
  public void setBefore(SkuReadVO skuReadVO) {
    before = skuReadVO;
  }

  /** 读取申请提交快照，供接口结果展示使用。 */
  public SkuReadVO getProposed() {
    return proposed;
  }

  /** 接收申请提交快照，保持接口结果展示的字段含义。 */
  public void setProposed(SkuReadVO skuReadVO) {
    proposed = skuReadVO;
  }

  /** 读取当前生效资料，供接口结果展示使用。 */
  public SkuReadVO getCurrent() {
    return current;
  }

  /** 接收当前生效资料，保持接口结果展示的字段含义。 */
  public void setCurrent(SkuReadVO skuReadVO) {
    current = skuReadVO;
  }

  /** 读取首次入库数量，供接口结果展示使用。 */
  public Integer getInitialQuantity() {
    return initialQuantity;
  }

  /** 接收首次入库数量，保持接口结果展示的字段含义。 */
  public void setInitialQuantity(Integer value) {
    initialQuantity = value;
  }

  /** 读取提交时SKU资料版本，供接口结果展示使用。 */
  public Integer getBaseSkuVersion() {
    return baseSkuVersion;
  }

  /** 接收提交时SKU资料版本，保持接口结果展示的字段含义。 */
  public void setBaseSkuVersion(Integer value) {
    baseSkuVersion = value;
  }

  /** 读取被驳回或撤回的原申请标识，供接口结果展示使用。 */
  public String getPreviousApplicationId() {
    return previousApplicationId;
  }

  /** 接收被驳回或撤回的原申请标识，保持接口结果展示的字段含义。 */
  public void setPreviousApplicationId(String value) {
    previousApplicationId = value;
  }

  /** 读取currentFillStatus，供接口结果展示使用。 */
  public String getCurrentFillStatus() {
    return currentFillStatus;
  }

  /** 接收currentFillStatus，保持接口结果展示的字段含义。 */
  public void setCurrentFillStatus(String value) {
    currentFillStatus = value;
  }

  /** 读取currentUpdatedAt，供接口结果展示使用。 */
  public String getCurrentUpdatedAt() {
    return currentUpdatedAt;
  }

  /** 接收currentUpdatedAt，保持接口结果展示的字段含义。 */
  public void setCurrentUpdatedAt(String value) {
    currentUpdatedAt = value;
  }

  /** 设置申请动作并返回当前对象，供连续组装数据。 */
  public SkuReviewVO actionType(String value) {
    setActionType(value);
    return this;
  }

  /** 设置申请前 SKU 状态并返回当前对象，供连续组装数据。 */
  public SkuReviewVO beforeSkuStatus(String value) {
    setBeforeSkuStatus(value);
    return this;
  }

  /** 设置审核通过后的目标 SKU 状态并返回当前对象，供连续组装数据。 */
  public SkuReviewVO targetSkuStatus(String value) {
    setTargetSkuStatus(value);
    return this;
  }

  /** 设置变更前快照并返回当前对象，供连续组装数据。 */
  public SkuReviewVO before(SkuReadVO skuReadVO) {
    setBefore(skuReadVO);
    return this;
  }

  /** 设置申请提交快照并返回当前对象，供连续组装数据。 */
  public SkuReviewVO proposed(SkuReadVO skuReadVO) {
    setProposed(skuReadVO);
    return this;
  }

  /** 设置当前生效资料并返回当前对象，供连续组装数据。 */
  public SkuReviewVO current(SkuReadVO skuReadVO) {
    setCurrent(skuReadVO);
    return this;
  }

  /** 设置首次入库数量并返回当前对象，供连续组装数据。 */
  public SkuReviewVO initialQuantity(Integer value) {
    setInitialQuantity(value);
    return this;
  }

  /** 设置提交时SKU资料版本并返回当前对象，供连续组装数据。 */
  public SkuReviewVO baseSkuVersion(Integer value) {
    setBaseSkuVersion(value);
    return this;
  }

  /** 设置被驳回或撤回的原申请标识并返回当前对象，供连续组装数据。 */
  public SkuReviewVO previousApplicationId(String value) {
    setPreviousApplicationId(value);
    return this;
  }

  /** 设置currentFillStatus并返回当前对象，供连续组装数据。 */
  public SkuReviewVO currentFillStatus(String value) {
    setCurrentFillStatus(value);
    return this;
  }

  /** 设置currentUpdatedAt并返回当前对象，供连续组装数据。 */
  public SkuReviewVO currentUpdatedAt(String value) {
    setCurrentUpdatedAt(value);
    return this;
  }
}
