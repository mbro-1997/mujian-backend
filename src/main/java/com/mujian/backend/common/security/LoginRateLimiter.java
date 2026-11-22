package com.mujian.backend.common.security;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.api.ApiFailure;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 账号/IP 共用一次原子准入；在途请求也占用五次预算，避免并发越过失败阈值。 */
@Component
public class LoginRateLimiter {
  private static final long WINDOW_MS = 900_000;
  private static final long LEASE_MS = 30_000;
  private static final DefaultRedisScript<Long> RESERVE =
      new DefaultRedisScript<>(
          """
          local clock = redis.call('TIME')
          local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
          for i = 1, 2 do
              redis.call('ZREMRANGEBYSCORE', KEYS[i], '-inf', now - tonumber(ARGV[2]))
              redis.call('ZREMRANGEBYSCORE', KEYS[i + 2], '-inf', now)
          end
          for i = 1, 2 do
              if redis.call('ZCARD', KEYS[i]) + redis.call('ZCARD', KEYS[i + 2]) >= 5 then
                  return 0
              end
          end
          for i = 3, 4 do
              redis.call('ZADD', KEYS[i], now + tonumber(ARGV[3]), ARGV[1])
              redis.call('PEXPIRE', KEYS[i], ARGV[3])
          end
          return 1
          """,
          Long.class);
  private static final DefaultRedisScript<Long> SETTLE =
      new DefaultRedisScript<>(
          """
          local clock = redis.call('TIME')
          local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
          local account = redis.call('ZSCORE', KEYS[3], ARGV[1])
          local ip = redis.call('ZSCORE', KEYS[4], ARGV[1])
          redis.call('ZREM', KEYS[3], ARGV[1])
          redis.call('ZREM', KEYS[4], ARGV[1])
          if not account or not ip or tonumber(account) <= now or tonumber(ip) <= now then
              return 0
          end
          if ARGV[2] == 'failure' then
              for i = 1, 2 do
                  redis.call('ZREMRANGEBYSCORE', KEYS[i], '-inf', now - tonumber(ARGV[3]))
                  redis.call('ZADD', KEYS[i], now, ARGV[1])
                  redis.call('PEXPIRE', KEYS[i], ARGV[3])
              end
          elseif ARGV[2] == 'success' then
              -- 同一 IP 可能正在攻击其他账号，成功只清本账号的历史失败。
              redis.call('DEL', KEYS[1])
          end
          return 1
          """,
          Long.class);

  private final StringRedisTemplate redis;
  private final String prefix;

  public LoginRateLimiter(
      StringRedisTemplate redis, @Value("${mj.environment:production}") String env) {
    this.redis = redis;
    prefix = "mujian:" + env + ":{login-budget}:";
  }

  /** 构造当前用途的稳定标识。 */
  private String key(String kind, String value) {
    try {
      return prefix
          + kind
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 在事务中为本次操作预留额度。 */
  public Attempt reserve(String username, String ip) {
    List<String> keys =
        List.of(
            key("account:failed:", username),
            key("ip:failed:", ip),
            key("account:inflight:", username),
            key("ip:inflight:", ip));
    String token = UUID.randomUUID().toString();
    try {
      if (!Long.valueOf(1)
          .equals(
              redis.execute(
                  RESERVE, keys, token, Long.toString(WINDOW_MS), Long.toString(LEASE_MS)))) {
        throw new ApiFailure(
            429, D2Codes.CODE_RATE_LIMITED, D2Codes.CODE_TOO_MANY_REQUESTS, Math.toIntExact(WINDOW_MS / 1000));
      }
      return new Attempt(keys, token);
    } catch (DataAccessException e) {
      throw ApiFailure.redis().causedBy(e);
    }
  }

  public final class Attempt implements AutoCloseable {
    private final List<String> keys;
    private final String token;
    private boolean settled;

    private Attempt(List<String> keys, String token) {
      this.keys = keys;
      this.token = token;
    }

    /** 将失败计入已预留的登录尝试。 */
    public void failure() {
      settle("failure");
    }

    /** 释放本次成功登录使用的尝试配额。 */
    public void success() {
      settle("success");
    }

    /** 确认本次预留的最终结果。 */
    private void settle(String outcome) {
      if (settled) throw new IllegalStateException("登录预算已经结算");
      try {
        Long result = redis.execute(SETTLE, keys, token, outcome, Long.toString(WINDOW_MS));
        settled = true;
        // 过期请求不能凭迟到的校验结果登录；释放失败由租约到期恢复。
        if (!"cancel".equals(outcome) && !Long.valueOf(1).equals(result)) {
          throw new ApiFailure(500, D2Codes.CODE_INTERNAL_ERROR, D2Codes.CODE_UNEXPECTED_ERROR);
        }
      } catch (DataAccessException e) {
        throw ApiFailure.redis().causedBy(e);
      }
    }

    /** 释放本次调用占用的资源。 */
    @Override
    public void close() {
      if (!settled) settle("cancel");
    }
  }
}
