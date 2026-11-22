package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 FactoryPublic，不是数据库实体。 */
public class FactoryPublicVO {
  private String id;

  private String name;

  private ImageViewVO avatar;

  /** 读取记录标识，供接口结果展示使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持接口结果展示的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取名称，供接口结果展示使用。 */
  public String getName() {
    return name;
  }

  /** 接收名称，保持接口结果展示的字段含义。 */
  public void setName(String value) {
    name = value;
  }

  /** 读取工厂头像，供接口结果展示使用。 */
  public ImageViewVO getAvatar() {
    return avatar;
  }

  /** 接收工厂头像，保持接口结果展示的字段含义。 */
  public void setAvatar(ImageViewVO imageViewVO) {
    avatar = imageViewVO;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public FactoryPublicVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置名称并返回当前对象，供连续组装数据。 */
  public FactoryPublicVO name(String value) {
    setName(value);
    return this;
  }

  /** 设置工厂头像并返回当前对象，供连续组装数据。 */
  public FactoryPublicVO avatar(ImageViewVO imageViewVO) {
    setAvatar(imageViewVO);
    return this;
  }
}
