package com.mujian.backend.common.security;

import com.mujian.backend.common.api.ApiFailure;
import jakarta.servlet.http.*;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.security.web.csrf.*;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;
import org.springframework.stereotype.Service;

/** 官方 indexed 仓库存储；已发布会话不再调用 save，活动用原子脚本修改现有 hash。 这样后台请求不续期，旧请求不能以 save 重建已退出会话，权限仍从 DB 读取。 */
@Service
public class SessionService {
  public record Handle(org.springframework.session.Session session) {}

  private final RedisIndexedSessionRepository repository;
  private final Clock clock;
  private final String prefix;
  private final boolean dev;
  private final SessionValueSerializer serializer = new SessionValueSerializer();

  public SessionService(
      RedisIndexedSessionRepository repository,
      Clock clock,
      @Value("${mj.environment:production}") String env,
      @Value("${spring.session.data.redis.namespace}") String namespace) {
    this.repository = repository;
    this.clock = clock;
    if (namespace.isBlank() || namespace.chars().anyMatch(Character::isWhitespace))
      throw new IllegalArgumentException("CONFIG_ERROR: invalid session namespace");
    prefix = namespace + ":";
    dev = "development".equals(env) || "test".equals(env);
    if (!Set.of("development", "test", "production").contains(env))
      throw new IllegalArgumentException("未知运行环境");
  }

  /** 返回当前会话使用的凭据名称。 */
  public String cookieName() {
    return dev ? "mj_admin_dev" : "__Host-mj_admin";
  }

  /** 提取接口携带的会话凭据。 */
  public String credential(HttpServletRequest r) {
    if (r.getRequestURI().startsWith("/api/admin/")) {
      if (r.getCookies() != null)
        for (Cookie c : r.getCookies()) if (cookieName().equals(c.getName())) return c.getValue();
      return null;
    }
    String h = r.getHeader("Authorization");
    if (h == null) return null;
    if (!h.startsWith("Bearer ")) throw ApiFailure.invalid();
    return h.substring(7);
  }

  /** 读取当前凭据对应的有效会话。 */
  public Handle load(String id) {
    if (id == null) return null;
    if (!id.matches("[0-9a-f-]{36}")) return null;
    try {
      if (check(id, false) <= 0) {
        revoke(id);
        return null;
      }
      RedisSession s = repository.findById(id);
      return s == null ? null : new Handle(s);
    } catch (ApiFailure e) {
      throw e;
    } catch (org.springframework.dao.DataAccessException e) {
      throw ApiFailure.redis().causedBy(e);
    }
  }

  /** 生成并保存新的会话凭据。 */
  public Handle create(String user, String type) {
    try {
      RedisSession s = repository.createSession();
      long now = clock.millis();
      long ttl = user == null ? 600_000L : "admin".equals(type) ? 14_400_000L : 2_592_000_000L;
      s.setMaxInactiveInterval(Duration.ofMillis(ttl));
      s.setAttribute("clientType", type);
      s.setAttribute("authenticatedAt", now);
      s.setAttribute("lastInteractiveAt", now);
      if (user != null) {
        s.setAttribute("userId", user);
        s.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, user);
      }
      s.setAttribute("csrf", new HttpSessionCsrfTokenRepository().generateToken(null));
      repository.save(s); // 唯一一次保存发生在凭据发布之前，失败不会返回登录成功。
      RedisOperations<String, Object> ops = repository.getSessionRedisOperations();
      ops.execute(
          (RedisCallback<Object>)
              c -> {
                c.hashCommands().hSet(bytes(key(s.getId())), bytes("d1:active"), bytes("1"));
                c.hashCommands()
                    .hSet(bytes(key(s.getId())), bytes("d1:last"), bytes(Long.toString(now)));
                c.hashCommands()
                    .hSet(bytes(key(s.getId())), bytes("d1:limit"), bytes(Long.toString(ttl)));
                return null;
              });
      return new Handle(s);
    } catch (org.springframework.dao.DataAccessException e) {
      throw ApiFailure.redis().causedBy(e);
    }
  }

  /** 在当前会话仍有效时续期。 */
  public void touch(Handle handle) {
    if (handle != null && check(handle.session().getId(), true) <= 0) {
      revoke(handle.session().getId());
      throw ApiFailure.unauthorized();
    }
  }

  /** 按当前用户的官方 Redis 会话索引撤销其全部管理凭据。 */
  public void revokeUser(String userId) {
    try {
      for (java.util.Map.Entry<String, ? extends org.springframework.session.Session> entry : repository.findByPrincipalName(userId).entrySet())
        if("admin".equals(entry.getValue().getAttribute("clientType"))) revoke(entry.getKey());
    } catch(org.springframework.dao.DataAccessException e){throw ApiFailure.redis().causedBy(e);}
  }

  /** 撤销指定会话。 */
  public void revoke(String id) {
    if (id == null) return;
    try {
      RedisOperations<String, Object> ops = repository.getSessionRedisOperations();
      ops.execute(
          (RedisCallback<Object>)
              c ->
                  c.scriptingCommands()
                      .eval(
                          bytes(
                              """
if redis.call('EXISTS',KEYS[1])==1 then
    redis.call('HSET',KEYS[1],'d1:active','0');
redis.call('PEXPIRE',KEYS[1],300000)
end;
return 1
"""),
                          ReturnType.INTEGER,
                          1,
                          bytes(key(id))));
      repository.deleteById(id);
      // 官方删除会保留短暂 hash 供索引清理；最终删除 hash 防止任何读取观察旧属性。
      ops.delete(key(id));
    } catch (org.springframework.dao.DataAccessException e) {
      throw ApiFailure.redis().causedBy(e);
    }
  }

  /** 核对当前状态是否允许继续。 */
  private long check(String id, boolean interactive) {
    try {
      long now = clock.millis();
      String script =
          """
if redis.call('HGET',KEYS[1],'d1:active')~='1' then
    return 0
end;
local last=tonumber(redis.call('HGET',KEYS[1],'d1:last'));
local limit=tonumber(redis.call('HGET',KEYS[1],'d1:limit'));
if not last or not limit then
    return 0
end;
local now=tonumber(ARGV[1]);
if now-last>=limit then
    redis.call('HSET',KEYS[1],'d1:active','0');
redis.call('PEXPIRE',KEYS[1],300000);
redis.call('DEL',KEYS[2]);
return -1
end;
if ARGV[2]=='1' and now>last then
    redis.call('HSET',KEYS[1],'d1:last',ARGV[1],'sessionAttr:lastInteractiveAt',ARGV[3],'lastAccessedTime',ARGV[4]);
redis.call('PEXPIRE',KEYS[1],limit+300000);
redis.call('PEXPIRE',KEYS[2],limit)
end;
return 1
""";
      Long result =
          repository
              .getSessionRedisOperations()
              .execute(
                  (RedisCallback<Long>)
                      c ->
                          c.scriptingCommands()
                              .eval(
                                  bytes(script),
                                  ReturnType.INTEGER,
                                  2,
                                  bytes(key(id)),
                                  bytes(prefix + "sessions:expires:" + id),
                                  bytes(Long.toString(now)),
                                  bytes(interactive ? "1" : "0"),
                                  serializer.serialize(now),
                                  serializer.serialize(System.currentTimeMillis())));
      return result == null ? 0 : result;
    } catch (org.springframework.dao.DataAccessException e) {
      throw ApiFailure.redis().causedBy(e);
    }
  }

  /** 构造当前用途的稳定标识。 */
  private String key(String id) {
    return prefix + "sessions:" + id;
  }

  /** 将凭据信息转换为存储字节。 */
  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  /** 构造管理端登录凭据。 */
  public void cookie(HttpServletResponse r, String id) {
    r.addHeader(
        "Set-Cookie",
        cookieName()
            + "="
            + (id == null ? "" : id)
            + "; Path=/; HttpOnly; SameSite=Lax"
            + (dev ? "" : "; Secure")
            + (id == null ? "; Max-Age=0" : ""));
  }

  /** 从 HTTP 请求恢复当前用户的会话。 */
  public Handle from(HttpServletRequest r) {
    return (Handle) r.getAttribute("sessionHandle");
  }

  /** 将会话凭据写入相应客户端响应。 */
  public void attach(HttpServletRequest r, Handle handle) {
    r.setAttribute("sessionHandle", handle);
  }

  /** 取得会话所属的用户标识。 */
  public String user(Handle handle) {
    return handle == null ? null : handle.session().getAttribute("userId");
  }

  /** 识别当前会话类型。 */
  public String type(Handle handle) {
    return handle == null ? null : handle.session().getAttribute("clientType");
  }
}
