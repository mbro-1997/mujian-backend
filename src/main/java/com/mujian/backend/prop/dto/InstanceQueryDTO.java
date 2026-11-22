package com.mujian.backend.prop.dto;

import java.time.*;

public class InstanceQueryDTO extends PropPageFilterDTO {
  private String skuId;

  private String status;

  /** 读取道具筛选标识，供请求绑定或查询条件传递使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具筛选标识，保持请求绑定或查询条件传递的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取状态筛选条件，供请求绑定或查询条件传递使用。 */
  public String getStatus() {
    return status;
  }

  /** 接收状态筛选条件，保持请求绑定或查询条件传递的字段含义。 */
  public void setStatus(String value) {
    status = value;
  }

  /** 设置道具筛选标识并返回当前对象，供连续组装数据。 */
  public InstanceQueryDTO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置状态筛选条件并返回当前对象，供连续组装数据。 */
  public InstanceQueryDTO status(String value) {
    setStatus(value);
    return this;
  }
}
