package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 ImageView，不是数据库实体。 */
public class ImageViewVO {
  private String url;

  private String expiresAt;

  /** 读取短时访问地址，供接口结果展示使用。 */
  public String getUrl() {
    return url;
  }

  /** 接收短时访问地址，保持接口结果展示的字段含义。 */
  public void setUrl(String value) {
    url = value;
  }

  /** 读取凭证或链接失效时间，供接口结果展示使用。 */
  public String getExpiresAt() {
    return expiresAt;
  }

  /** 接收凭证或链接失效时间，保持接口结果展示的字段含义。 */
  public void setExpiresAt(String value) {
    expiresAt = value;
  }

  /** 设置短时访问地址并返回当前对象，供连续组装数据。 */
  public ImageViewVO url(String value) {
    setUrl(value);
    return this;
  }

  /** 设置凭证或链接失效时间并返回当前对象，供连续组装数据。 */
  public ImageViewVO expiresAt(String value) {
    setExpiresAt(value);
    return this;
  }
}
