package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 OwnedSku，不是数据库实体。 */
public class OwnedSkuVO {
  private String id;

  private String skuCode;

  private String factoryId;

  private String assetType;

  private String skuStatus;

  private Integer version;

  private SkuReadVO fields;

  private String pendingApplicationId;

  private String currentQrId;

  private Boolean canEditOptions;

  private Boolean canApplyUp;

  private Boolean canApplyDown;

  private String createdAt;

  private String updatedAt;

  private String factoryName;

  private String fillStatus;

  private InventorySummaryVO inventorySummary;

  private Boolean canAdjustInventory;

  private String latestQrId;

  /** 读取记录标识，供接口结果展示使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持接口结果展示的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取SKU 编码，供接口结果展示使用。 */
  public String getSkuCode() {
    return skuCode;
  }

  /** 接收SKU 编码，保持接口结果展示的字段含义。 */
  public void setSkuCode(String value) {
    skuCode = value;
  }

  /** 读取工厂标识，供接口结果展示使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收工厂标识，保持接口结果展示的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取资产类型，供接口结果展示使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持接口结果展示的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取道具上下架状态，供接口结果展示使用。 */
  public String getSkuStatus() {
    return skuStatus;
  }

  /** 接收道具上下架状态，保持接口结果展示的字段含义。 */
  public void setSkuStatus(String value) {
    skuStatus = value;
  }

  /** 读取乐观锁版本，供接口结果展示使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持接口结果展示的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取道具业务字段，供接口结果展示使用。 */
  public SkuReadVO getFields() {
    return fields;
  }

  /** 接收道具业务字段，保持接口结果展示的字段含义。 */
  public void setFields(SkuReadVO skuReadVO) {
    fields = skuReadVO;
  }

  /** 读取pendingApplicationId，供接口结果展示使用。 */
  public String getPendingApplicationId() {
    return pendingApplicationId;
  }

  /** 接收pendingApplicationId，保持接口结果展示的字段含义。 */
  public void setPendingApplicationId(String value) {
    pendingApplicationId = value;
  }

  /** 读取当前有效二维码记录标识，供接口结果展示使用。 */
  public String getCurrentQrId() {
    return currentQrId;
  }

  /** 接收当前有效二维码记录标识，保持接口结果展示的字段含义。 */
  public void setCurrentQrId(String value) {
    currentQrId = value;
  }

  /** 读取是否允许修改道具选项，供接口结果展示使用。 */
  public Boolean getCanEditOptions() {
    return canEditOptions;
  }

  /** 接收是否允许修改道具选项，保持接口结果展示的字段含义。 */
  public void setCanEditOptions(Boolean value) {
    canEditOptions = value;
  }

  /** 读取是否允许申请上架，供接口结果展示使用。 */
  public Boolean getCanApplyUp() {
    return canApplyUp;
  }

  /** 接收是否允许申请上架，保持接口结果展示的字段含义。 */
  public void setCanApplyUp(Boolean value) {
    canApplyUp = value;
  }

  /** 读取是否允许申请下架，供接口结果展示使用。 */
  public Boolean getCanApplyDown() {
    return canApplyDown;
  }

  /** 接收是否允许申请下架，保持接口结果展示的字段含义。 */
  public void setCanApplyDown(Boolean value) {
    canApplyDown = value;
  }

  /** 读取创建时间，供接口结果展示使用。 */
  public String getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持接口结果展示的字段含义。 */
  public void setCreatedAt(String value) {
    createdAt = value;
  }

  /** 读取更新时间，供接口结果展示使用。 */
  public String getUpdatedAt() {
    return updatedAt;
  }

  /** 接收更新时间，保持接口结果展示的字段含义。 */
  public void setUpdatedAt(String value) {
    updatedAt = value;
  }

  /** 读取factoryName，供接口结果展示使用。 */
  public String getFactoryName() {
    return factoryName;
  }

  /** 接收factoryName，保持接口结果展示的字段含义。 */
  public void setFactoryName(String value) {
    factoryName = value;
  }

  /** 读取资料填充状态，供接口结果展示使用。 */
  public String getFillStatus() {
    return fillStatus;
  }

  /** 接收资料填充状态，保持接口结果展示的字段含义。 */
  public void setFillStatus(String value) {
    fillStatus = value;
  }

  /** 读取库存摘要，供接口结果展示使用。 */
  public InventorySummaryVO getInventorySummary() {
    return inventorySummary;
  }

  /** 接收库存摘要，保持接口结果展示的字段含义。 */
  public void setInventorySummary(InventorySummaryVO inventorySummaryVO) {
    inventorySummary = inventorySummaryVO;
  }

  /** 读取是否允许调整库存，供接口结果展示使用。 */
  public Boolean getCanAdjustInventory() {
    return canAdjustInventory;
  }

  /** 接收是否允许调整库存，保持接口结果展示的字段含义。 */
  public void setCanAdjustInventory(Boolean value) {
    canAdjustInventory = value;
  }

  /** 读取最新二维码记录标识，供接口结果展示使用。 */
  public String getLatestQrId() {
    return latestQrId;
  }

  /** 接收最新二维码记录标识，保持接口结果展示的字段含义。 */
  public void setLatestQrId(String value) {
    latestQrId = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SKU 编码并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO skuCode(String value) {
    setSkuCode(value);
    return this;
  }

  /** 设置工厂标识并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO factoryId(String value) {
    setFactoryId(value);
    return this;
  }

  /** 设置资产类型并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO assetType(String value) {
    setAssetType(value);
    return this;
  }

  /** 设置道具上下架状态并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO skuStatus(String value) {
    setSkuStatus(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置道具业务字段并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO fields(SkuReadVO skuReadVO) {
    setFields(skuReadVO);
    return this;
  }

  /** 设置pendingApplicationId并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO pendingApplicationId(String value) {
    setPendingApplicationId(value);
    return this;
  }

  /** 设置当前有效二维码记录标识并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO currentQrId(String value) {
    setCurrentQrId(value);
    return this;
  }

  /** 设置是否允许修改道具选项并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO canEditOptions(Boolean value) {
    setCanEditOptions(value);
    return this;
  }

  /** 设置是否允许申请上架并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO canApplyUp(Boolean value) {
    setCanApplyUp(value);
    return this;
  }

  /** 设置是否允许申请下架并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO canApplyDown(Boolean value) {
    setCanApplyDown(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO createdAt(String value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO updatedAt(String value) {
    setUpdatedAt(value);
    return this;
  }

  /** 设置factoryName并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO factoryName(String value) {
    setFactoryName(value);
    return this;
  }

  /** 设置资料填充状态并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO fillStatus(String value) {
    setFillStatus(value);
    return this;
  }

  /** 设置库存摘要并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO inventorySummary(InventorySummaryVO inventorySummaryVO) {
    setInventorySummary(inventorySummaryVO);
    return this;
  }

  /** 设置是否允许调整库存并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO canAdjustInventory(Boolean value) {
    setCanAdjustInventory(value);
    return this;
  }

  /** 设置最新二维码记录标识并返回当前对象，供连续组装数据。 */
  public OwnedSkuVO latestQrId(String value) {
    setLatestQrId(value);
    return this;
  }
}
