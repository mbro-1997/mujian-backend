package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 PropDetail，不是数据库实体。 */
public class PropDetailVO {
  private PropDetailCardVO prop;

  private PropDetailDetailsVO details;

  private AvailabilityVO availability;

  /** 读取道具卡片，供接口结果展示使用。 */
  public PropDetailCardVO getProp() {
    return prop;
  }

  /** 接收道具卡片，保持接口结果展示的字段含义。 */
  public void setProp(PropDetailCardVO propDetailCardVO) {
    prop = propDetailCardVO;
  }

  /** 读取道具详细资料，供接口结果展示使用。 */
  public PropDetailDetailsVO getDetails() {
    return details;
  }

  /** 接收道具详细资料，保持接口结果展示的字段含义。 */
  public void setDetails(PropDetailDetailsVO propDetailDetailsVO) {
    details = propDetailDetailsVO;
  }

  /** 读取租期可租结果，供接口结果展示使用。 */
  public AvailabilityVO getAvailability() {
    return availability;
  }

  /** 接收租期可租结果，保持接口结果展示的字段含义。 */
  public void setAvailability(AvailabilityVO availabilityVO) {
    availability = availabilityVO;
  }

  /** 设置道具卡片并返回当前对象，供连续组装数据。 */
  public PropDetailVO prop(PropDetailCardVO propDetailCardVO) {
    setProp(propDetailCardVO);
    return this;
  }

  /** 设置道具详细资料并返回当前对象，供连续组装数据。 */
  public PropDetailVO details(PropDetailDetailsVO propDetailDetailsVO) {
    setDetails(propDetailDetailsVO);
    return this;
  }

  /** 设置租期可租结果并返回当前对象，供连续组装数据。 */
  public PropDetailVO availability(AvailabilityVO availabilityVO) {
    setAvailability(availabilityVO);
    return this;
  }
}
