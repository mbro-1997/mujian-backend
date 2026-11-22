package com.mujian.backend.prop.dto;

import java.time.*;

public class LogQueryDTO extends PropPageFilterDTO {
  private String snId;

  /** 读取台账查询目标标识，供请求绑定或查询条件传递使用。 */
  public String getSnId() {
    return snId;
  }

  /** 接收台账查询目标标识，保持请求绑定或查询条件传递的字段含义。 */
  public void setSnId(String value) {
    snId = value;
  }

  /** 设置台账查询目标标识并返回当前对象，供连续组装数据。 */
  public LogQueryDTO snId(String value) {
    setSnId(value);
    return this;
  }
}
