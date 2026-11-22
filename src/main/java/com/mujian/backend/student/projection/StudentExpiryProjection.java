package com.mujian.backend.student.projection;

import java.time.*;
import java.util.*;

/** 已到期候选；进入事务后仍重新加锁核验状态。 */
public class StudentExpiryProjection {
  private String id;
  private String userId;

  /** 读取id。 */
  public String getId() {
    return id;
  }

  /** 设置id。 */
  public void setId(String id) {
    this.id = id;
  }

  /** 读取userId。 */
  public String getUserId() {
    return userId;
  }

  /** 设置userId。 */
  public void setUserId(String userId) {
    this.userId = userId;
  }

  /** 组装id。 */
  public StudentExpiryProjection id(String id) {
    this.id = id;
    return this;
  }

  /** 组装userId。 */
  public StudentExpiryProjection userId(String userId) {
    this.userId = userId;
    return this;
  }
}
