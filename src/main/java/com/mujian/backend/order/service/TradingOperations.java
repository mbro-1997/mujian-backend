package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.AdminOperationAuditDO;
import com.mujian.backend.casework.mapper.AdminOperationAuditMapper;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.domain.TradingOperationDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.TradingOperationMapper;
import com.mujian.backend.order.vo.TradingReceipt;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 本批命令防重及恢复，资金执行不依赖可清理的编辑回执。 */
@Service
public class TradingOperations {
  public record Result(
      int schemaVersion,
      String resourceType,
      String resourceId,
      int resourceVersion,
      String appliedAt,
      ScanSnapshot scan,
      CloseSnapshot close,
      String evidenceDigest,String paymentId) {}

  /** 扫码回执保留当次核对结果，原号重放不能投影后来的数量。 */
  public record ScanSnapshot(
      boolean duplicate, String itemId, int checkedQuantity, int expectedQuantity, int version) {}

  private final TradingOperationMapper tradingOperationMapper;
  private final FactoryAccess factoryAccess;
  private final VerificationAccess verificationAccess;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final AdminOperationAuditMapper adminOperationAuditMapper;

  /** 复用现有角色、管理权限、用户根锁与序列化。 */
  public TradingOperations(
      TradingOperationMapper tradingOperationMapper,
      FactoryAccess factoryAccess,
      VerificationAccess verificationAccess,
      ObjectMapper objectMapper,
      Clock clock,
      AdminOperationAuditMapper adminOperationAuditMapper) {
    this.adminOperationAuditMapper = adminOperationAuditMapper;
    this.tradingOperationMapper = tradingOperationMapper;
    this.factoryAccess = factoryAccess;
    this.verificationAccess = verificationAccess;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 重新核对原操作当前权限，不因持有回执而绕过授权。 */
  public void authorize(String actor, TradingOperation tradingOperationOperation) {
    if (tradingOperationOperation.permission() != null)
      verificationAccess.admin(actor, tradingOperationOperation.permission());
    else if (!tradingOperationOperation
        .role()
        .equals(factoryAccess.context(actor, false).roleCode()))
      throw ApiFailure.denied(
          D2Codes.CODE_DEMANDER.equals(tradingOperationOperation.role())
              ? TradingReason.DEMANDER_ROLE_REQUIRED.name()
              : TradingReason.SUPPLIER_ROLE_REQUIRED.name());
  }

  /** 在本批固定锁序中先锁用户，供管理配置串行化。 */
  public void lock(String actor) {
    factoryAccess.lock(actor);
  }

  /** 版本和外部报价之前读取同号结果，同号换内容必须拒绝。 */
  public TradingReceipt replay(TradingCommand tradingCommand) {
    authorize(tradingCommand.actor(), tradingCommand.operation());
    TradingOperationDO tradingOperationDO =
        tradingOperationMapper.selectOperationByActorAndRequest(
            tradingCommand.actor(), tradingCommand.requestId());
    if (tradingOperationDO == null) return null;
    if (!tradingOperationDO.requestHash().equals(tradingCommand.requestHash())
        || !tradingOperationDO.operationCode().equals(tradingCommand.operation().name()))
      throw conflict(D2Codes.CODE_IDEMPOTENCY_KEY_REUSED);
    Result result = objectMapper.readValue(tradingOperationDO.resultJson(), Result.class);
    return new TradingReceipt(
        tradingOperationDO.requestId(),
        tradingOperationDO.operationCode(),
        result.resourceType(),
        result.resourceId(),
        result.resourceVersion(),
        D2Codes.CODE_REPLAYED,
        result.appliedAt());
  }

  /** 查询本人持久回执，管理恢复另要求恢复菜单权限。 */
  public TradingReceipt recover(String actor, String requestId, boolean admin) {
    if (admin) verificationAccess.admin(actor, "admin:trade:operation:read");
    TradingOperationDO tradingOperationDO =
        tradingOperationMapper.selectOperationByActorAndRequest(actor, requestId);
    if (tradingOperationDO == null) throw missing();
    TradingOperation tradingOperationOperation =
        TradingOperation.valueOf(tradingOperationDO.operationCode());
    if (admin != (tradingOperationOperation.permission() != null)) throw missing();
    authorize(actor, tradingOperationOperation);
    Result result = objectMapper.readValue(tradingOperationDO.resultJson(), Result.class);
    return new TradingReceipt(
        tradingOperationDO.requestId(),
        tradingOperationDO.operationCode(),
        result.resourceType(),
        result.resourceId(),
        result.resourceVersion(),
        TradingReceiptOutcome.recovered.name(),
        result.appliedAt());
  }

  /** 调用者已持有订单根锁；再次校验原请求及权限后读取冻结的扫码结果。 */
  public ScanSnapshot replayScan(TradingCommand tradingCommand) {
    if (replay(tradingCommand) == null) return null;
    TradingOperationDO tradingOperationDO =
        tradingOperationMapper.selectOperationByActorAndRequest(
            tradingCommand.actor(), tradingCommand.requestId());
    ScanSnapshot scanSnapshot = objectMapper.readValue(tradingOperationDO.resultJson(), Result.class).scan();
    if (scanSnapshot == null) throw new IllegalStateException("scan receipt missing");
    return scanSnapshot;
  }

  /** 与业务写入同事务保存最小结果，不存长详情或渠道签名。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public TradingReceipt record(
      TradingCommand tradingCommand, String resourceType, String resourceId, int version) {
    return record(tradingCommand, resourceType, resourceId, version, null);
  }

  /** 补缴重试保留当次付款主单，即使该债后来启动了另一轮付款。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordPayment(TradingCommand tradingCommand,String debt,int version,String paymentId){
    persist(tradingCommand,"order_compensation_receivable",debt,version,null,null,null,null,paymentId);
  }

  /** 登记定制付款命令及原请求结果。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCustomPayment(TradingCommand tradingCommand,String order,int version,String paymentId){
    persist(tradingCommand,"custom_order",order,version,null,null,null,null,paymentId);
  }

  /** 按原请求回放已登记的付款结果。 */
  public String replayPayment(TradingCommand tradingCommand){
    if(replay(tradingCommand)==null)return null;
    TradingOperationDO tradingOperationDO=tradingOperationMapper.selectOperationByActorAndRequest(tradingCommand.actor(),tradingCommand.requestId());
    return objectMapper.readValue(tradingOperationDO.resultJson(),Result.class).paymentId();
  }

  /** 与普通回执共用唯一键，只为扫码保存明确的当次增量结果。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public TradingReceipt record(
      TradingCommand tradingCommand,
      String resourceType,
      String resourceId,
      int version,
      ScanSnapshot scanSnapshot) {
    return persist(tradingCommand, resourceType, resourceId, version, scanSnapshot, null, null, null);
  }

  /** 人工管理操作的原始理由进入既有审计。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public TradingReceipt recordReason(
      TradingCommand command, String type, String id, int version, String reason) {
    return persist(command, type, id, version, null, null, reason, null);
  }

  /** 关闭回执冻结本次退押义务编号和实际处置动作。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public TradingReceipt recordClose(
      TradingCommand command,
      String type,
      String id,
      int version,
      CloseSnapshot closeSnapshot,
      String reason) {
    return persist(command, type, id, version, null, closeSnapshot, reason, null);
  }

  public record CloseSnapshot(String checkId, String actionId, java.util.List<String> refundIds) {}

  /** 调用者已校验订单归属与原请求，重放取本次冻结的义务集合。 */
  public CloseSnapshot replayClose(TradingCommand command) {
    if (replay(command) == null) return null;
    TradingOperationDO tradingOperationDO =
        tradingOperationMapper.selectOperationByActorAndRequest(
            command.actor(), command.requestId());
    return objectMapper.readValue(tradingOperationDO.resultJson(), Result.class).close();
  }

  /** 读取原请求已绑定的业务来源。 */
  public TradingReceipt priorBinding(TradingCommand command, String digest) {
    String request =
        tradingOperationMapper.selectEvidenceBinding(
            command.actor(), command.operation().name(), digest);
    return request == null ? null : recover(command.actor(), request, false);
  }

  /** 保存命令与业务来源的绑定。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public TradingReceipt recordBinding(
      TradingCommand tradingCommand, String type, String id, int version, String digest) {
    return persist(tradingCommand, type, id, version, null, null, null, digest);
  }

  /** 同一业务事务存回执和必要审计，理由不进入资金来源。 */
  private TradingReceipt persist(
      TradingCommand tradingCommand,
      String resourceType,
      String resourceId,
      int version,
      ScanSnapshot scanSnapshot,
      CloseSnapshot closeSnapshot,
      String reason,
      String evidenceDigest) {
    return persist(tradingCommand,resourceType,resourceId,version,scanSnapshot,closeSnapshot,reason,evidenceDigest,null);
  }

  /** 保存可用于后续回放的命令结果。 */
  private TradingReceipt persist(TradingCommand tradingCommand,String resourceType,String resourceId,int version,
      ScanSnapshot scanSnapshot,CloseSnapshot closeSnapshot,String reason,String evidenceDigest,String paymentId) {
    Result result =
        new Result(
            paymentId!=null ? 4 : evidenceDigest != null ? 3 : scanSnapshot == null && closeSnapshot == null ? 1 : 2,
            resourceType,
            resourceId,
            version,
            time(now(clock)),
            scanSnapshot,
            closeSnapshot,
            evidenceDigest,paymentId);
    tradingOperationMapper.insertTradingOperation(
        new TradingOperationDO(
            Ids.next(),
            tradingCommand.actor(),
            tradingCommand.requestId(),
            tradingCommand.operation().name(),
            tradingCommand.requestHash(),
            resourceType,
            resourceId,
            objectMapper.writeValueAsString(result),
            now(clock)));
    if (tradingCommand.operation().permission() != null)
      adminOperationAuditMapper.insertAdminOperationAudit(
          new AdminOperationAuditDO()
              .id(Ids.next())
              .auditNo("TRADE-" + Ids.next())
              .adminUserId(tradingCommand.actor())
              .permissionCode(tradingCommand.operation().permission())
              .operationCode(tradingCommand.operation().name())
              .operationName(tradingCommand.operation().name())
              .targetModule(
                  (tradingCommand.operation().permission().startsWith("admin:fulfillment:")
                      || tradingCommand.operation().permission().startsWith("admin:receipt-issue:"))
                      ? "05"
                      : tradingCommand.operation().permission().startsWith("admin:custom:") ? "11" : tradingCommand.operation().permission().startsWith("admin:arbitration:") || tradingCommand.operation().permission().startsWith("admin:claim:") ? "06" : "04")
              .targetType(resourceType)
              .targetId(resourceId)
              .requestId(tradingCommand.requestId())
              .idempotencyKey(
                  "trading:" + tradingCommand.actor() + ":" + tradingCommand.requestId())
              .afterSnapshotJson(objectMapper.writeValueAsString(result))
              .operationReason(reason)
              .resultStatus(TradingState.success.name())
              .createdAt(now(clock)));
    return new TradingReceipt(
        tradingCommand.requestId(),
        tradingCommand.operation().name(),
        resourceType,
        resourceId,
        version,
        TradingReceiptOutcome.applied.name(),
        result.appliedAt());
  }
}
