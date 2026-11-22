package com.mujian.backend.material.vo;

import java.time.*;
import java.util.*;

/** 材料接口明确响应；授权范围和私有签名期限不变。 */
public class SensitiveImageVO {
  private int position;
  private String url;

  /** 读取position。 */
  public int getPosition() {
    return position;
  }

  /** 设置position。 */
  public void setPosition(int position) {
    this.position = position;
  }

  /** 读取url。 */
  public String getUrl() {
    return url;
  }

  /** 设置url。 */
  public void setUrl(String url) {
    this.url = url;
  }

  /** 组装position。 */
  public SensitiveImageVO position(int position) {
    this.position = position;
    return this;
  }

  /** 组装url。 */
  public SensitiveImageVO url(String url) {
    this.url = url;
    return this;
  }
}
