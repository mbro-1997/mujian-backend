package com.mujian.backend.admin.mapper;

import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommandAuditMapper {
  record Replay(String digest, String resultJson) {}

  /** 同一操作、操作者与请求键的原成功结果。 */
  Replay selectReceipt(@Param("actor") String actor, @Param("operation") String operation, @Param("key") String key);

  /** 管理高风险命令与审计同一事务提交。 */
  void insertReceipt(@Param("id") String id,
      @Param("actor") String actor,
      @Param("permission") String permission,
      @Param("operation") String operation,
      @Param("label") String label,
      @Param("module") String module,
      @Param("targetType") String targetType,
      @Param("targetId") String targetId,
      @Param("trace") String trace,
      @Param("key") String key,
      @Param("digest") String digest,
      @Param("before") String before,
      @Param("result") String result,
      @Param("reason") String reason,
      @Param("at") LocalDateTime at);
}
