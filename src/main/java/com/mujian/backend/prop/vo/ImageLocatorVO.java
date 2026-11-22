package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 ImageLocatorDTO，不是数据库实体。 */
public class ImageLocatorVO {
  private String sourceType;

  private String sourceId;

  private String section;

  private Integer index;

  /** 读取业务来源类型，供接口结果展示使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收业务来源类型，保持接口结果展示的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取业务来源标识，供接口结果展示使用。 */
  public String getSourceId() {
    return sourceId;
  }

  /** 接收业务来源标识，保持接口结果展示的字段含义。 */
  public void setSourceId(String value) {
    sourceId = value;
  }

  /** 读取材料所属快照区段，供接口结果展示使用。 */
  public String getSection() {
    return section;
  }

  /** 接收材料所属快照区段，保持接口结果展示的字段含义。 */
  public void setSection(String value) {
    section = value;
  }

  /** 读取图片在材料集合中的序号，供接口结果展示使用。 */
  public Integer getIndex() {
    return index;
  }

  /** 接收图片在材料集合中的序号，保持接口结果展示的字段含义。 */
  public void setIndex(Integer value) {
    index = value;
  }

  /** 设置业务来源类型并返回当前对象，供连续组装数据。 */
  public ImageLocatorVO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置业务来源标识并返回当前对象，供连续组装数据。 */
  public ImageLocatorVO sourceId(String value) {
    setSourceId(value);
    return this;
  }

  /** 设置材料所属快照区段并返回当前对象，供连续组装数据。 */
  public ImageLocatorVO section(String value) {
    setSection(value);
    return this;
  }

  /** 设置图片在材料集合中的序号并返回当前对象，供连续组装数据。 */
  public ImageLocatorVO index(Integer value) {
    setIndex(value);
    return this;
  }
}
