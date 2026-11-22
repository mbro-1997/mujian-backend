package com.mujian.backend.coupon.mapper;

import org.apache.ibatis.annotations.Param;
import com.mujian.backend.coupon.dto.OperationsInput.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationsWriteMapper {
  /** 确认所有工厂范围对象真实存在。 */
  int countFactories(@Param("ids") List<String> ids);

  /** 固定编码下建立下一版本。 */
  int nextTemplateVersion(@Param("code") String code);

  /** 创建新的模板草稿。 */
  void insertTemplate(@Param("id") String id, @Param("actor") String actor, @Param("input") Template input, @Param("version") int version, @Param("at") LocalDateTime at);

  /** 修改尚未发布的版本。 */
  void updateTemplate(@Param("id") String id, @Param("input") Template input, @Param("at") LocalDateTime at);

  /** 草稿替换范围集合。 */
  void removeScope(@Param("id") String id);

  /** 一次写入本模板全部适用工厂。 */
  void insertScope(@Param("id") String id, @Param("scopes") List<Scope> scopes, @Param("at") LocalDateTime at);

  record Scope(String id, String factoryId) {}

  /** 仅调整状态，既有发放快照不动。 */
  void setTemplateStatus(@Param("id") String id, @Param("status") String status, @Param("at") LocalDateTime at);

  /** 创建活动并冻结创建意图。 */
  void insertActivity(@Param("id") String id,
      @Param("actor") String actor,
      @Param("key") String key,
      @Param("digest") String digest,
      @Param("input") Activity input,
      @Param("at") LocalDateTime at);

  /** 更新草稿并推进活动版本。 */
  int updateActivity(@Param("id") String id, @Param("input") Activity input);

  /** 条件变更状态。 */
  int setActivityStatus(@Param("id") String id, @Param("status") String status, @Param("version") int version, @Param("at") LocalDateTime at);

  /** 同一首次事件下递增版本。 */
  int nextRuleVersion(@Param("event") String event);

  /** 创建事件规则草稿。 */
  void insertRule(@Param("id") String id, @Param("actor") String actor, @Param("input") Rule input, @Param("version") int version, @Param("at") LocalDateTime at);

  /** 调整规则草稿引用。 */
  void updateRule(@Param("id") String id, @Param("templateId") String templateId);

  /** 旧规则结束，已经产生的义务继续履行。 */
  void endActiveRule(@Param("event") String event, @Param("at") LocalDateTime at);

  /** 变更选定规则版本的状态。 */
  void setRuleStatus(@Param("id") String id, @Param("status") String status, @Param("at") LocalDateTime at);
}
