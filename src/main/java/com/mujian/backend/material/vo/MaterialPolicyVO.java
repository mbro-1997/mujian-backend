package com.mujian.backend.material.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class MaterialPolicyVO {
  private List<String> contentTypes;
  private int maxBytes;
  private int maxPixels;
  private int receiptTtlSeconds;

  /** 读取contentTypes。 */
  public List<String> getContentTypes() {
    return contentTypes;
  }

  /** 设置contentTypes。 */
  public void setContentTypes(List<String> contentTypes) {
    this.contentTypes = contentTypes;
  }

  /** 读取maxBytes。 */
  public int getMaxBytes() {
    return maxBytes;
  }

  /** 设置maxBytes。 */
  public void setMaxBytes(int maxBytes) {
    this.maxBytes = maxBytes;
  }

  /** 读取maxPixels。 */
  public int getMaxPixels() {
    return maxPixels;
  }

  /** 设置maxPixels。 */
  public void setMaxPixels(int maxPixels) {
    this.maxPixels = maxPixels;
  }

  /** 读取receiptTtlSeconds。 */
  public int getReceiptTtlSeconds() {
    return receiptTtlSeconds;
  }

  /** 设置receiptTtlSeconds。 */
  public void setReceiptTtlSeconds(int receiptTtlSeconds) {
    this.receiptTtlSeconds = receiptTtlSeconds;
  }

  /** 组装contentTypes。 */
  public MaterialPolicyVO contentTypes(List<String> contentTypes) {
    this.contentTypes = contentTypes;
    return this;
  }

  /** 组装maxBytes。 */
  public MaterialPolicyVO maxBytes(int maxBytes) {
    this.maxBytes = maxBytes;
    return this;
  }

  /** 组装maxPixels。 */
  public MaterialPolicyVO maxPixels(int maxPixels) {
    this.maxPixels = maxPixels;
    return this;
  }

  /** 组装receiptTtlSeconds。 */
  public MaterialPolicyVO receiptTtlSeconds(int receiptTtlSeconds) {
    this.receiptTtlSeconds = receiptTtlSeconds;
    return this;
  }
}
