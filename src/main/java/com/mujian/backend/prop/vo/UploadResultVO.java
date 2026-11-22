package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 UploadResult，不是数据库实体。 */
public class UploadResultVO {
  private String uploadId;

  private String purpose;

  private String expiresAt;

  /** 读取上传引用凭证，供接口结果展示使用。 */
  public String getUploadId() {
    return uploadId;
  }

  /** 接收上传引用凭证，保持接口结果展示的字段含义。 */
  public void setUploadId(String value) {
    uploadId = value;
  }

  /** 读取材料上传用途，供接口结果展示使用。 */
  public String getPurpose() {
    return purpose;
  }

  /** 接收材料上传用途，保持接口结果展示的字段含义。 */
  public void setPurpose(String value) {
    purpose = value;
  }

  /** 读取凭证或链接失效时间，供接口结果展示使用。 */
  public String getExpiresAt() {
    return expiresAt;
  }

  /** 接收凭证或链接失效时间，保持接口结果展示的字段含义。 */
  public void setExpiresAt(String value) {
    expiresAt = value;
  }

  /** 设置上传引用凭证并返回当前对象，供连续组装数据。 */
  public UploadResultVO uploadId(String value) {
    setUploadId(value);
    return this;
  }

  /** 设置材料上传用途并返回当前对象，供连续组装数据。 */
  public UploadResultVO purpose(String value) {
    setPurpose(value);
    return this;
  }

  /** 设置凭证或链接失效时间并返回当前对象，供连续组装数据。 */
  public UploadResultVO expiresAt(String value) {
    setExpiresAt(value);
    return this;
  }
}
