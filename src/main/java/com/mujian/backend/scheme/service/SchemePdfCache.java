package com.mujian.backend.scheme.service;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.D2Values;
import com.mujian.backend.scheme.domain.PdfFileMetadata;
import com.mujian.backend.scheme.domain.SchemeExportDO;
import java.time.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 只保存有时限的成品定位，未命中不以数据库或磁盘恢复缓存。 */
@Service
public class SchemePdfCache {
  public static final String TEMPLATE = "scheme_v1";
  public static final String WATERMARK = "幕间方案参考 / MUHOU";
  private static final DefaultRedisScript<Long> REMOVE =
      new DefaultRedisScript<>(
          "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end; return"
              + " 0",
          Long.class);
  private final StringRedisTemplate stringRedisTemplate;
  private final Clock clock;
  private final String prefix;

  /** 使用既有环境隔离配置，业务前缀独立。 */
  public SchemePdfCache(
      StringRedisTemplate stringRedisTemplate,
      Clock clock,
      @Value("${mj.environment:production}") String environment) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.clock = clock;
    prefix = "mj:" + environment + ":scheme-pdf-export:";
  }

  /** 生成设置的所有内容维度参与键，水印版本固定对应当前文本。 */
  public String key(String schemeId, int contentVersion, boolean hidePrice) {
    return prefix
        + schemeId
        + ":"
        + contentVersion
        + ":"
        + hidePrice
        + ":"
        + TEMPLATE
        + ":watermark_muhou_v1";
  }

  /** 读取不续期；内嵌截止同时支持可控时钟和真实Redis TTL。 */
  public PdfFileMetadata get(String key) {
    try {
      String value = stringRedisTemplate.opsForValue().get(key);
      if (value == null) return null;
      String[] parts = value.split(":");
      if (parts.length != 3
          || !parts[0].matches("[0-9a-f]{32}")
          || !parts[1].matches("[0-9]{1,16}")
          || !parts[2].matches("[0-9]{1,16}")
          || clock.millis() >= Long.parseLong(parts[1])
          || clock.millis() >= Long.parseLong(parts[2])) {
        stringRedisTemplate.execute(REMOVE, List.of(key), value);
        return null;
      }
      return new PdfFileMetadata(
          parts[0],
          LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[2])), D2Values.ZONE));
    } catch (org.springframework.dao.DataAccessException exception) {
      throw ApiFailure.redis().causedBy(exception);
    }
  }

  /** 成功事实提交后才发布定位，始终从generatedAt计算三分钟。 */
  public void put(SchemeExportDO schemeExportDO) {
    Instant expires =
        schemeExportDO.generatedAt().atZone(D2Values.ZONE).toInstant().plusSeconds(180);
    Duration remaining = Duration.between(clock.instant(), expires);
    if (remaining.isZero() || remaining.isNegative()) return;
    try {
      stringRedisTemplate
          .opsForValue()
          .set(
              key(
                  schemeExportDO.schemeId(),
                  schemeExportDO.sourceContentVersion(),
                  schemeExportDO.hidePrice()),
              schemeExportDO.id()
                  + ":"
                  + expires.toEpochMilli()
                  + ":"
                  + schemeExportDO.fileExpireAt().atZone(D2Values.ZONE).toInstant().toEpochMilli(),
              remaining);
    } catch (org.springframework.dao.DataAccessException exception) {
      throw ApiFailure.redis().causedBy(exception);
    }
  }

  /** 只删除当前指向该成品的映射，避免误删并发新成品。 */
  public void remove(String key, String exportId) {
    try {
      String value = stringRedisTemplate.opsForValue().get(key);
      if (value != null && value.startsWith(exportId + ":"))
        stringRedisTemplate.execute(REMOVE, List.of(key), value);
    } catch (org.springframework.dao.DataAccessException exception) {
      throw ApiFailure.redis().causedBy(exception);
    }
  }
}
