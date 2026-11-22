package com.mujian.backend.admin.service;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.admin.mapper.CommandAuditMapper;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.material.service.VerificationAccess;
import java.security.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class CommandAuditService {
  public record Command(String actor, String key, String trace) {}

  private final CommandAuditMapper commandAuditMapper;
  private final LoginMapper loginMapper;
  private final VerificationAccess verificationAccess;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 高风险操作共用真实操作者、数据库根锁和审计回执。 */
  public CommandAuditService(
      CommandAuditMapper commandAuditMapper,
      LoginMapper loginMapper,
      VerificationAccess verificationAccess,
      ObjectMapper objectMapper,
      Clock clock) {
    this.commandAuditMapper = commandAuditMapper;
    this.loginMapper = loginMapper;
    this.verificationAccess = verificationAccess;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 在操作者根锁内重新鉴权并恢复原结果，失败不保留审计键。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public <T, I> T execute(
      Command cmd,
      String permission,
      String operation,
      String label,
      String module,
      String targetType,
      String targetId,
      I input,
      Class<T> resultType,
      Function<T, String> idOf,
      Supplier<T> action) {
    return execute(
        cmd,
        permission,
        operation,
        label,
        module,
        targetType,
        targetId,
        input,
        resultType,
        idOf,
        ignored -> action.get());
  }

  /** 修改已有对象时，在对象根锁内摘录变更前的公开状态。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public <T, I> T execute(
      Command cmd,
      String permission,
      String operation,
      String label,
      String module,
      String targetType,
      String targetId,
      I input,
      Class<T> resultType,
      Function<T, String> idOf,
      Function<java.util.function.Consumer<Object>, T> action) {
    loginMapper.selectUserByIdForUpdate(cmd.actor());
    verificationAccess.admin(cmd.actor(), permission);
    String digest;
    try {
      digest =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          objectMapper.writeValueAsBytes(
                              Arrays.asList(operation, targetId, input))));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
    CommandAuditMapper.Replay previous =
        commandAuditMapper.selectReceipt(cmd.actor(), operation, cmd.key());
    if (previous != null) {
      if (!digest.equals(previous.digest())) throw conflict(D2Codes.CODE_IDEMPOTENCY_KEY_REUSED);
      return objectMapper.readValue(previous.resultJson(), resultType);
    }
    tools.jackson.databind.node.ObjectNode before = objectMapper.createObjectNode();
    T result =
        action.apply(
            value -> {
              tools.jackson.databind.JsonNode node = objectMapper.valueToTree(value);
              for (String field :
                  List.of(
                      "enabled",
                      "status",
                      "caseStatus",
                      "couponStatus",
                      "ruleVersion",
                      "version",
                      "templateVersion",
                      "issuedCount",
                      "verificationStatus",
                      "sortNo",
                      "visible",
                      "menuName",
                      "ruleTitle",
                      "menuIds")) if (node.has(field)) before.set(field, node.get(field));
            });
    commandAuditMapper.insertReceipt(
        Ids.next(),
        cmd.actor(),
        permission,
        operation,
        label,
        module,
        targetType,
        idOf.apply(result),
        cmd.trace(),
        cmd.key(),
        digest,
        objectMapper.writeValueAsString(before),
        objectMapper.writeValueAsString(result),
        objectMapper.valueToTree(input).path("reason").asString(null),
        now(clock));
    return result;
  }
}
