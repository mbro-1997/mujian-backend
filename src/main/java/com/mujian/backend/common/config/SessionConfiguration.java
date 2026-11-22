package com.mujian.backend.common.config;

import com.mujian.backend.common.security.SessionValueSerializer;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

@Configuration(proxyBeanMethods = false)
@org.springframework.scheduling.annotation.EnableScheduling
public class SessionConfiguration {
  /** 注册会话生命周期通知。 */
  @Bean
  org.springframework.data.redis.listener.RedisMessageListenerContainer sessionEvents(
      RedisConnectionFactory factory, RedisIndexedSessionRepository repository) {
    RedisMessageListenerContainer c = new org.springframework.data.redis.listener.RedisMessageListenerContainer();
    c.setConnectionFactory(factory);
    c.addMessageListener(
        repository,
        new org.springframework.data.redis.listener.PatternTopic("__keyevent@0__:expired"));
    c.addMessageListener(
        repository, new org.springframework.data.redis.listener.PatternTopic("__keyevent@0__:del"));
    return c;
  }

  /** 提供统一业务时钟。 */
  @Bean
  Clock applicationClock() {
    return Clock.systemUTC();
  }

  /** 提供会话专用的 Redis 读写配置。 */
  @Bean
  RedisTemplate<String, Object> sessionRedisOperations(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> t = new RedisTemplate<String, Object>();
    t.setConnectionFactory(factory);
    t.setKeySerializer(new StringRedisSerializer());
    t.setHashKeySerializer(new StringRedisSerializer());
    t.setValueSerializer(new SessionValueSerializer());
    t.setHashValueSerializer(new SessionValueSerializer());
    t.afterPropertiesSet();
    return t;
  }

  /** 配置会话仓储及到期策略。 */
  @Bean
  RedisIndexedSessionRepository sessionRepository(
      RedisTemplate<String, Object> sessionRedisOperations,
      @Value("${spring.session.data.redis.namespace}") String namespace) {
    RedisIndexedSessionRepository r = new RedisIndexedSessionRepository(sessionRedisOperations);
    if (namespace.isBlank() || namespace.chars().anyMatch(Character::isWhitespace))
      throw new IllegalStateException("CONFIG_ERROR: invalid session namespace");
    r.setRedisKeyNamespace(namespace);
    r.setDefaultSerializer(new SessionValueSerializer());
    return r;
  }
}
