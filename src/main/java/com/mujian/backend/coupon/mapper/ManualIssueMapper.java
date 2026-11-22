package com.mujian.backend.coupon.mapper;

import org.apache.ibatis.annotations.Param;
import com.mujian.backend.coupon.dto.IssueCouponsInput;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ManualIssueMapper {
  record Receipt(
      String distributionId,
      String status,
      String targetCount,
      String issuedQuantity,
      String completedAt) {}

  record Batch(
      String id,
      String owner,
      String digest,
      String mode,
      long targets,
      long quantity,
      LocalDateTime completedAt) {}

  /** 同键读回成功批次及所有者。 */
  Batch selectByKey(@Param("key") String key);

  /** 事务内占用唯一键，提交前必须填入实际结果。 */
  int insertBatch(@Param("id") String id,
      @Param("actor") String actor,
      @Param("key") String key,
      @Param("digest") String digest,
      @Param("input") IssueCouponsInput input,
      @Param("config") String config,
      @Param("at") LocalDateTime at);

  /** 按已确认名单或全部有效租赁方一次取数。 */
  List<String> selectTargetUsers(@Param("ids") List<String> ids);

  /** 所有券与流水写完后保存最终人数与张数。 */
  int completeBatch(@Param("id") String id, @Param("targets") long targets, @Param("quantity") long quantity, @Param("at") LocalDateTime at);

  /** 审计与五张业务写表共用原事务。 */
  void insertIssueAudit(@Param("id") String id,
      @Param("actor") String actor,
      @Param("batch") String batch,
      @Param("key") String key,
      @Param("trace") String trace,
      @Param("reason") String reason,
      @Param("at") LocalDateTime at);
}
