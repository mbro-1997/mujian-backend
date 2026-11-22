package com.mujian.backend.material.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class MaterialConsentVO {
  private String version;
  private String text;

  /** 读取version。 */
  public String getVersion() {
    return version;
  }

  /** 设置version。 */
  public void setVersion(String version) {
    this.version = version;
  }

  /** 读取text。 */
  public String getText() {
    return text;
  }

  /** 设置text。 */
  public void setText(String text) {
    this.text = text;
  }

  /** 组装version。 */
  public MaterialConsentVO version(String version) {
    this.version = version;
    return this;
  }

  /** 组装text。 */
  public MaterialConsentVO text(String text) {
    this.text = text;
    return this;
  }
}
