package com.mujian.backend.infrastructure.storage;

import java.time.*;
import java.util.*;

public interface PrivateMaterialStore {
  record Stored(String key, Map<String, String> metadata, Instant lastModified, String versionId) {}

  record Page(List<Stored> objects, String nextMarker) {}

  /** 将已校验的对象内容写入指定路径。 */
  void put(String key, byte[] bytes, String contentType, Map<String, String> metadata);

  /** 读取对象元数据以验证上传结果。 */
  Stored head(String key);

  /** 为已授权的对象生成限时访问地址。 */
  String sign(String key, Instant expiresAt);

  /** 读取下一批可供核对的对象及游标。 */
  Page list(String marker);

  /** 检查对象是否满足允许删除的配置约束。 */
  boolean deletionPolicyVerified();

  /** 删除已确认可清理的指定对象。 */
  void delete(String key, String versionId);
}
