package com.mujian.backend.common.security;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.api.ApiFailure;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class D2RateLimiter {
  private final StringRedisTemplate redis;
  private final String prefix;
  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],60000)"
              + " end; if n>tonumber(ARGV[1]) then return"
              + " -math.max(1,math.ceil(redis.call('PTTL',KEYS[1])/1000)) end; return 0",
          Long.class);

  public D2RateLimiter(StringRedisTemplate r, @Value("${mj.environment:production}") String env) {
    redis = r;
    prefix = "mujian:" + env + ":d2budget:";
  }

  /** 核对当前状态是否允许继续。 */
  public void check(String category, String subject, int limit) {
    try {
      String hash =
          java.util.HexFormat.of()
              .formatHex(
                  java.security.MessageDigest.getInstance("SHA-256")
                      .digest(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      Long count =
          redis.execute(SCRIPT, List.of(prefix + category + ":" + hash), Integer.toString(limit));
      if (count == null) throw ApiFailure.redis();
      if (count < 0)
        throw new ApiFailure(429, D2Codes.CODE_RATE_LIMITED, D2Codes.CODE_TOO_MANY_REQUESTS, Math.toIntExact(-count));
    } catch (ApiFailure e) {
      throw e;
    } catch (Exception e) {
      throw ApiFailure.redis().causedBy(e);
    }
  }
}
