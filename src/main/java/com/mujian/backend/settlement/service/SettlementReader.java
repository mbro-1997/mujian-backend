package com.mujian.backend.settlement.service;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.AdminOperationAuditDO;
import com.mujian.backend.casework.mapper.AdminOperationAuditMapper;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.FulfillmentReason;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.RentalOrderReader;
import com.mujian.backend.order.service.TradingTokens;
import com.mujian.backend.settlement.dto.SettlementQuery;
import com.mujian.backend.settlement.mapper.*;
import com.mujian.backend.settlement.vo.SettlementViews.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SettlementReader {
  private static final String READ = "admin:settlement:read", ACK = "admin:settlement:acknowledge";
  private final SettlementReadMapper settlementReadMapper;
  private final SharingMapper sharingMapper;
  private final AdminOperationAuditMapper adminOperationAuditMapper;
  private final RentalOrderReader rentalOrderReader;
  private final VerificationAccess verificationAccess;
  private final TradingTokens tradingTokens;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配可见批次与已阅审计所需依赖。 */
  public SettlementReader(
      SettlementReadMapper settlementReadMapper,
      SharingMapper sharingMapper,
      AdminOperationAuditMapper adminOperationAuditMapper,
      RentalOrderReader rentalOrderReader,
      VerificationAccess verificationAccess,
      TradingTokens tradingTokens,
      ObjectMapper objectMapper,
      Clock clock) {
    this.settlementReadMapper = settlementReadMapper;
    this.sharingMapper = sharingMapper;
    this.adminOperationAuditMapper = adminOperationAuditMapper;
    this.rentalOrderReader = rentalOrderReader;
    this.verificationAccess = verificationAccess;
    this.tradingTokens = tradingTokens;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 解析当前工厂归属或核实管理员读取权限。 */
  private String scope(String actor, boolean admin) {
    if (admin) {
      verificationAccess.admin(actor, READ);
      return null;
    }
    return rentalOrderReader.supplierFactory(actor);
  }

  /** 在授权数据范围内分页并返回总数。 */
  public PageResult<Summary> list(String actor, boolean admin, SettlementQuery settlementQuery) {
    String owner = scope(actor, admin);
    if (!admin) settlementQuery = new SettlementQuery(owner, "settled", settlementQuery.fromDate(), settlementQuery.toDate(), settlementQuery.page());
    return PageResult.of(settlementReadMapper.selectSettlementBatchPageByQuery(settlementQuery), settlementReadMapper.selectFactorySettlementBatchCountByQuery(settlementQuery), settlementQuery.page());
  }

  /** 在调用者的可见范围内定位批次，越权与不存在同形。 */
  private Summary owned(String actor, boolean admin, String batch) {
    Summary settlementViewsSummary = settlementReadMapper.selectSettlementBatchSummaryById(id(batch), scope(actor, admin));
    if (settlementViewsSummary == null) throw missing();
    return settlementViewsSummary;
  }

  /** 子列表只核对批次可见性，金额由摘要查询计算。 */
  private void requireVisible(String actor, boolean admin, String batch) {
    if (!settlementReadMapper.existsVisibleBatch(id(batch), scope(actor, admin))) throw missing();
  }

  /** 限定该批次并分页读取订单结算明细。 */
  public PageResult<Item> items(String actor, boolean admin, String batch, PageQuery pageQuery) {
    requireVisible(actor, admin, batch);
    return PageResult.of(
        settlementReadMapper.selectSettlementItemsByBatch(batch, pageQuery), settlementReadMapper.itemCount(batch), pageQuery);
  }

  /** 限定该批次并分页读取原来源分佣及解冻记录。 */
  public PageResult<Execution> executions(String actor, boolean admin, String batch, PageQuery pageQuery) {
    requireVisible(actor, admin, batch);
    return PageResult.of(
        settlementReadMapper.selectWechatProfitSharingOrderExecutions(batch, pageQuery), settlementReadMapper.executionCount(batch), pageQuery);
  }

  /** 以稳定顺序绑定批次汇总与逐来源进度。 */
  private String digest(Summary summary) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      sha.update(objectMapper.writeValueAsBytes(summary));
      for (String row : settlementReadMapper.selectFactsFingerprint(summary.id()))
        sha.update(row.getBytes(StandardCharsets.US_ASCII));
      return HexFormat.of().formatHex(sha.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 校验查看范围并取得原记录的金额和处理事实。 */
  public Detail detail(String actor, boolean admin, String batch) {
    Summary summary = owned(actor, admin, batch);
    SettlementReadMapper.Info info = settlementReadMapper.selectSettlementPaymentInfoByBatch(batch, admin ? actor : null);
    if (info == null)
      throw new ApiFailure(
          500, D2Codes.CODE_DATA_INTEGRITY_ERROR, FulfillmentReason.SETTLEMENT_DATA_INCONSISTENT.name());
    return new Detail(
        summary,
        info.receiver(),
        info.masked(),
        time(info.checked()),
        info.exception(),
        admin && verificationAccess.has(actor, ACK)
            ? tradingTokens.settlement(actor, batch, digest(summary))
            : null,
        time(info.acknowledged()));
  }

  /** 复核查看人和批次快照后保存一次已阅审计。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Acknowledgement acknowledge(
      TradingCommand tradingCommand, String batch, SettlementQuery.Acknowledge acknowledge) {
    verificationAccess.admin(tradingCommand.actor(), ACK);
    if (sharingMapper.lockBatch(id(batch)) == null) throw missing();
    SettlementReadMapper.Audit old =
        settlementReadMapper.selectAdminOperationAuditByActor(batch, tradingCommand.actor(), tradingCommand.requestId());
    if (old != null) {
      if (!tradingCommand.requestHash().equals(old.digest()))
        throw conflict(FulfillmentReason.IDEMPOTENCY_CONFLICT.name());
      return new Acknowledgement(old.id(), batch, time(old.at()));
    }
    Summary summary = settlementReadMapper.selectSettlementBatchSummaryById(batch, null);
    String hash = digest(summary);
    tradingTokens.verifySettlement(tradingCommand.actor(), batch, hash, acknowledge.snapshotToken());
    String key = Ids.next();
    LocalDateTime at = now(clock);
    Acknowledgement result = new Acknowledgement(key, batch, time(at));
    adminOperationAuditMapper.insertAdminOperationAudit(
        new AdminOperationAuditDO()
            .id(key)
            .auditNo("SET-" + key)
            .adminUserId(tradingCommand.actor())
            .permissionCode(ACK)
            .operationCode(tradingCommand.operation().name())
            .operationName("结算记录已阅")
            .targetModule("04")
            .targetType("factory_settlement_batch")
            .targetId(batch)
            .requestId(tradingCommand.requestId())
            .idempotencyKey(tradingCommand.requestId())
            .operationReason(acknowledge.remark())
            .beforeSnapshotJson(objectMapper.writeValueAsString(Map.of("snapshotHash", hash)))
            .afterSnapshotJson(
                objectMapper.writeValueAsString(
                    view(
                        "snapshotHash",
                        hash,
                        "requestHash",
                        tradingCommand.requestHash(),
                        "result",
                        result,
                        "remark",
                        acknowledge.remark())))
            .resultStatus("success")
            .createdAt(at));
    return result;
  }
}
