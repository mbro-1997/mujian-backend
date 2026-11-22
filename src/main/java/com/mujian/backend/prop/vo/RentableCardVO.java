package com.mujian.backend.prop.vo;

public class RentableCardVO {
  private PropCardVO prop;
  private AvailabilityVO availability;

  /** 读取道具卡片，供接口结果展示使用。 */
  public PropCardVO getProp() {
    return prop;
  }

  /** 读取租期可租结果，供接口结果展示使用。 */
  public AvailabilityVO getAvailability() {
    return availability;
  }

  /** 设置道具卡片并返回当前对象，供连续组装数据。 */
  public RentableCardVO prop(PropCardVO propCardVO) {
    prop = propCardVO;
    return this;
  }

  /** 设置租期可租结果并返回当前对象，供连续组装数据。 */
  public RentableCardVO availability(AvailabilityVO availabilityVO) {
    availability = availabilityVO;
    return this;
  }
}
