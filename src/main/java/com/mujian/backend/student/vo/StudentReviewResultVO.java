package com.mujian.backend.student.vo;

import java.time.*;
import java.util.*;

/** 学生审核在共同结果上保留有效期字段，包括驳回时的null。 */
public class StudentReviewResultVO
    extends com.mujian.backend.verification.vo.VerificationReviewResultVO {
  private String expireAt;

  /** 读取expireAt。 */
  public String getExpireAt() {
    return expireAt;
  }

  /** 设置expireAt。 */
  public void setExpireAt(String expireAt) {
    this.expireAt = expireAt;
  }

  /** 组装expireAt。 */
  public StudentReviewResultVO expireAt(String expireAt) {
    this.expireAt = expireAt;
    return this;
  }
}
