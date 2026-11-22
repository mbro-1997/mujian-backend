package com.mujian.backend.common.dictionary.projection;
import java.time.*;
import java.util.*;
/** 完整字典状态查询；用于管理选项及锁定校验，不另建缓存。 */
public class DictionaryRecordProjection {
 private String id;
 private String optionGroup;
 private String optionCode;
 private String optionLabel;
 private Integer sortOrder;
 private Integer enabled;
 private String remark;
 private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 /** 读取id。 */
 public String getId() { return id; }
 /** 设置id。 */
 public void setId(String id) { this.id = id; }
 /** 读取optionGroup。 */
 public String getOptionGroup() { return optionGroup; }
 /** 设置optionGroup。 */
 public void setOptionGroup(String optionGroup) { this.optionGroup = optionGroup; }
 /** 读取optionCode。 */
 public String getOptionCode() { return optionCode; }
 /** 设置optionCode。 */
 public void setOptionCode(String optionCode) { this.optionCode = optionCode; }
 /** 读取optionLabel。 */
 public String getOptionLabel() { return optionLabel; }
 /** 设置optionLabel。 */
 public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }
 /** 读取sortOrder。 */
 public Integer getSortOrder() { return sortOrder; }
 /** 设置sortOrder。 */
 public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
 /** 读取enabled。 */
 public Integer getEnabled() { return enabled; }
 /** 设置enabled。 */
 public void setEnabled(Integer enabled) { this.enabled = enabled; }
 /** 读取remark。 */
 public String getRemark() { return remark; }
 /** 设置remark。 */
 public void setRemark(String remark) { this.remark = remark; }
 /** 读取createdAt。 */
 public LocalDateTime getCreatedAt() { return createdAt; }
 /** 设置createdAt。 */
 public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
 /** 读取updatedAt。 */
 public LocalDateTime getUpdatedAt() { return updatedAt; }
 /** 设置updatedAt。 */
 public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
 /** 组装id。 */
 public DictionaryRecordProjection id(String id) { this.id = id; return this; }
 /** 组装optionGroup。 */
 public DictionaryRecordProjection optionGroup(String optionGroup) { this.optionGroup = optionGroup; return this; }
 /** 组装optionCode。 */
 public DictionaryRecordProjection optionCode(String optionCode) { this.optionCode = optionCode; return this; }
 /** 组装optionLabel。 */
 public DictionaryRecordProjection optionLabel(String optionLabel) { this.optionLabel = optionLabel; return this; }
 /** 组装sortOrder。 */
 public DictionaryRecordProjection sortOrder(Integer sortOrder) { this.sortOrder = sortOrder; return this; }
 /** 组装enabled。 */
 public DictionaryRecordProjection enabled(Integer enabled) { this.enabled = enabled; return this; }
 /** 组装remark。 */
 public DictionaryRecordProjection remark(String remark) { this.remark = remark; return this; }
 /** 组装createdAt。 */
 public DictionaryRecordProjection createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
 /** 组装updatedAt。 */
 public DictionaryRecordProjection updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
}
