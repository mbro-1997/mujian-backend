package com.mujian.backend.prop.projection;

public class PropApplicantProjection {
  private String nickname;

  /** 读取申请人显示名称，供查询结果映射使用。 */
  public String getNickname() {
    return nickname;
  }

  /** 接收申请人显示名称，保持查询结果映射的字段含义。 */
  public void setNickname(String value) {
    nickname = value;
  }
}
