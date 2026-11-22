package com.mujian.backend.customer.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.customer.mapper.ServiceContextMapper;
import com.mujian.backend.customer.vo.ServiceViews.*;
import com.mujian.backend.factory.service.FactoryAccess;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ServiceContextService {
  private final ServiceContextMapper serviceContextMapper;
  private final FactoryAccess factoryAccess;
  private final StringRedisTemplate stringRedisTemplate;
  private final String prefix;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  /** Redis 仅承接原令牌短时取回，持久事实存于来源上下文。 */
  public ServiceContextService(
      ServiceContextMapper serviceContextMapper,
      FactoryAccess factoryAccess,
      StringRedisTemplate stringRedisTemplate,
      @Value("${mj.environment}") String environment,
      Clock clock) {
    this.serviceContextMapper = serviceContextMapper;
    this.factoryAccess = factoryAccess;
    this.stringRedisTemplate = stringRedisTemplate;
    prefix = "mj:" + environment + ":service-context:";
    this.clock = clock;
  }

  private record Actor(String role, String factoryId) {}

  /** 受限账号仍可咨询；入驻申请身份只在客服域内生效。 */
  private Actor actor(String user) {
    String role = factoryAccess.context(user, false).roleCode();
    if (D2Codes.CODE_SUPPLIER.equals(role))
      return new Actor(FulfillmentRole.factory.name(), factoryAccess.profile(user, false, false).getId());
    if (D2Codes.CODE_DEMANDER.equals(role)) return new Actor(FulfillmentRole.buyer.name(), null);
    if (role == null && serviceContextMapper.hasApplication(user))
      return new Actor("applicant", null);
    throw ApiFailure.denied(D2Codes.CODE_PERMISSION_DENIED);
  }

  /** 申请人即使提交其他来源类型，也不能进入买家查询。 */
  private String permittedType(Actor actor, String type) {
    if (!"applicant".equals(actor.role())) return type;
    if (type != null && !"factory_onboarding".equals(type))
      throw ApiFailure.denied(D2Codes.CODE_PERMISSION_DENIED);
    return "factory_onboarding";
  }

  /** 分页查询授权来源；按来源精确恢复时附上本人上下文定位及案件进度。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<ServiceSource> sources(
      String user, String type, String sourceId, PageQuery page) {
    if (sourceId != null && type == null) throw ApiFailure.invalid();
    Actor actor = actor(user);
    String permitted = permittedType(actor, type);
    String key = sourceId == null ? null : id(sourceId);
    long count = serviceContextMapper.countSources(user, actor.factoryId(), permitted, key);
    List<ServiceSource> items = new ArrayList<>();
    if (count > page.offset()) {
      for (SourceRow sourceRow :
          serviceContextMapper.selectSources(user, actor.factoryId(), permitted, key, page)) {
        Source source = new Source(sourceRow.sourceType(), sourceRow.sourceId());
        String contextId =
            key == null ? null : serviceContextMapper.latestContext(user, actor.role(), source);
        Progress progress = key == null ? null : serviceContextMapper.sourceProgress(user, source);
        items.add(
            new ServiceSource(source, sourceRow.title(), sourceRow.status(), contextId, progress, sourceRow.path()));
      }
    }
    return PageResult.of(items, count, page);
  }

  /** 管理员建案和用户制卡都走当前身份及同一来源归属查询。 */
  public SourceRow owned(String user, Source source) {
    return owned(user, actor(user), source);
  }

  /** 按已经确认的客服身份和真实来源主键核对归属。 */
  private SourceRow owned(String user, Actor actor, Source source) {
    List<SourceRow> rows =
        serviceContextMapper.selectSources(
            user,
            actor.factoryId(),
            permittedType(actor, source.sourceType()),
            id(source.sourceId()),
            new PageQuery(1, 1));
    if (rows.isEmpty()) throw missing();
    return rows.get(0);
  }

  /** 已有历史状态按上下文读回，未使用的令牌过期仅影响再次投递。 */
  private Context view(ContextRow contextRow, SourceRow authorized) {
    String status = contextRow.contextStatus();
    if ("created".equals(status) && !now(clock).isBefore(contextRow.expireAt())) status = "expired";
    return new Context(
        contextRow.id(),
        new Source(contextRow.sourceType(), contextRow.sourceId()),
        contextRow.title(),
        contextRow.statusSnapshot(),
        contextRow.summary(),
        "compensation_receivable".equals(contextRow.sourceType()) ? authorized.path() : contextRow.returnPagePath(),
        status,
        time(contextRow.expireAt()),
        contextRow.adminCaseId() == null
            ? null
            : serviceContextMapper.linkedProgress(contextRow.id(), contextRow.userId()));
  }

  /** 令牌摘要不用于对外展示。 */
  private String digest(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 只有原身份、原来源、未消费且未到期的原请求才能恢复明文。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ContextCard prepare(String user, String key, Source source) {
    factoryAccess.lock(user);
    Actor actor = actor(user);
    SourceRow authorized = owned(user, actor, source);
    String role = actor.role();
    ContextRow previous = serviceContextMapper.selectByRequest(key);
    if (previous != null) {
      if (!user.equals(previous.userId()) || !role.equals(previous.activeRole())) throw missing();
      if (!source.sourceId().equals(previous.sourceId())
          || !source.sourceType().equals(previous.sourceType()))
        throw conflict(D2Codes.CODE_IDEMPOTENCY_KEY_REUSED);
      Context context = view(previous, authorized);
      if ("expired".equals(context.contextStatus())) throw conflict(BusinessFailureReason.CONTEXT_EXPIRED.name());
      if (!"created".equals(context.contextStatus())) throw conflict(BusinessFailureReason.CONTEXT_ALREADY_USED.name());
      String token = stringRedisTemplate.opsForValue().get(prefix + previous.id());
      if (token == null || !previous.tokenHash().equals(digest(token)))
        throw conflict(BusinessFailureReason.CONTEXT_EXPIRED.name());
      return new ContextCard(context, token);
    }
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
        contextId = Ids.next();
    LocalDateTime at = now(clock), expires = at.plusMinutes(10);
    serviceContextMapper.insertContext(
        contextId, key, user, role, authorized, digest(token), at, expires);
    stringRedisTemplate.opsForValue().set(prefix + contextId, token, Duration.ofMinutes(10));
    return new ContextCard(view(serviceContextMapper.selectContext(contextId, false), authorized), token);
  }

  /** 即使知道 ID，也必须与当前身份和真实来源归属匹配。 */
  @Transactional(readOnly = true)
  public Context detail(String user, String id) {
    ContextRow contextRow = serviceContextMapper.selectContext(id(id), false);
    Actor actor = actor(user);
    if (contextRow == null || !user.equals(contextRow.userId())) throw missing();
    boolean historical =
        FulfillmentRole.factory.name().equals(actor.role())
            && "applicant".equals(contextRow.activeRole())
            && "factory_onboarding".equals(contextRow.sourceType());
    if (!actor.role().equals(contextRow.activeRole()) && !historical) throw missing();
    SourceRow authorized = owned(user, actor, new Source(contextRow.sourceType(), contextRow.sourceId()));
    return view(contextRow, authorized);
  }
}
