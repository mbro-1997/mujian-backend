package com.mujian.backend.prop.dto;

/** 道具详情及单道具可租查询只接收租期，不混入目录或审核条件。 */
public class PropRentalPeriodQueryDTO {
  private String startDate;
  private String endDate;

  /** 读取租期开始日期。 */
  public String getStartDate() {
    return startDate;
  }

  /** 设置租期开始日期。 */
  public void setStartDate(String value) {
    startDate = value;
  }

  /** 读取租期结束日期。 */
  public String getEndDate() {
    return endDate;
  }

  /** 设置租期结束日期。 */
  public void setEndDate(String value) {
    endDate = value;
  }
}
