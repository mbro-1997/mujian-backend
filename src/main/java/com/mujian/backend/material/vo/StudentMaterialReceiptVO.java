package com.mujian.backend.material.vo;

import java.time.*;
import java.util.*;

/** 材料接口明确响应；授权范围和私有签名期限不变。 */
public class StudentMaterialReceiptVO {
  private String uploadId;
  private String expiresAt;

  /** 读取uploadId。 */
  public String getUploadId() {
    return uploadId;
  }

  /** 设置uploadId。 */
  public void setUploadId(String uploadId) {
    this.uploadId = uploadId;
  }

  /** 读取expiresAt。 */
  public String getExpiresAt() {
    return expiresAt;
  }

  /** 设置expiresAt。 */
  public void setExpiresAt(String expiresAt) {
    this.expiresAt = expiresAt;
  }

  /** 组装uploadId。 */
  public StudentMaterialReceiptVO uploadId(String uploadId) {
    this.uploadId = uploadId;
    return this;
  }

  /** 组装expiresAt。 */
  public StudentMaterialReceiptVO expiresAt(String expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }
}
