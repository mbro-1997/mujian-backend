package com.mujian.backend.prop.projection;

public class PropReviewCountProjection {
  private String category;

  private Integer amount;

  /** 读取申请分类，供查询结果映射使用。 */
  public String getCategory() {
    return category;
  }

  /** 接收申请分类，保持查询结果映射的字段含义。 */
  public void setCategory(String value) {
    category = value;
  }

  /** 读取统计数量，供查询结果映射使用。 */
  public Integer getAmount() {
    return amount;
  }

  /** 接收统计数量，保持查询结果映射的字段含义。 */
  public void setAmount(Integer value) {
    amount = value;
  }
}
