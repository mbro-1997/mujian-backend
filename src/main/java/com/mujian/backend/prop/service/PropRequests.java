package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.PropReviewSources.*;
import static com.mujian.backend.infrastructure.persistence.MappingInputs.first;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.prop.converter.PropReviewConverter;
import com.mujian.backend.prop.domain.PropOperationRecordDO;
import com.mujian.backend.prop.domain.PropQrExceptionRecordDO;
import com.mujian.backend.prop.domain.PropSkuAuditDO;
import com.mujian.backend.prop.domain.PropSnAdjustmentAuditDO;
import com.mujian.backend.prop.domain.PropStockAdjustmentAuditDO;
import com.mujian.backend.prop.dto.PropAuditFactsDTO.OperationSnapshot;
import com.mujian.backend.prop.mapper.PropOperationRecordMapper;
import com.mujian.backend.prop.mapper.PropQrExceptionRecordMapper;
import com.mujian.backend.prop.mapper.PropSkuAuditMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentAuditMapper;
import com.mujian.backend.prop.mapper.PropStockAdjustmentAuditMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.projection.PropReviewProjection;
import com.mujian.backend.prop.utils.PropJson;
import com.mujian.backend.prop.vo.CommandResultVO;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PropRequests {
  private final PropOperationRecordMapper propOperationRecordMapper;
  private final PropQrExceptionRecordMapper propQrExceptionRecordMapper;
  private final PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper;
  private final PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper;
  private final PropSkuAuditMapper propSkuAuditMapper;
  private final PropJson propJson;
  private final PropAccess propAccess;
  private final Clock clock;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropRequests(
      PropOperationRecordMapper propOperationRecordMapper,
      PropQrExceptionRecordMapper propQrExceptionRecordMapper,
      PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper,
      PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper,
      PropSkuAuditMapper propSkuAuditMapper,
      PropJson propJson,
      PropAccess propAccess,
      Clock clock) {
    this.propOperationRecordMapper = propOperationRecordMapper;
    this.propQrExceptionRecordMapper = propQrExceptionRecordMapper;
    this.propStockAdjustmentAuditMapper = propStockAdjustmentAuditMapper;
    this.propSnAdjustmentAuditMapper = propSnAdjustmentAuditMapper;
    this.propSkuAuditMapper = propSkuAuditMapper;
    this.propJson = propJson;
    this.propAccess = propAccess;
    this.clock = clock;
  }

  public record Found(String type, String skuId, String requestHash, CommandResultVO result) {}

  /** 按既定字段构造业务结果，保持状态和时间表达。 */
  public CommandResultVO result(
      String operation,
      String outcome,
      String sku,
      String type,
      String target,
      int version,
      String state,
      java.time.LocalDateTime at) {
    return new CommandResultVO()
        .operation(operation)
        .outcome(outcome)
        .skuId(sku)
        .targetType(type)
        .targetId(target)
        .version(version)
        .status(state)
        .completedAt(time(at));
  }

  /** 根据已提交或既有待审申请构造操作结果。 */
  public CommandResultVO submitted(
      String type, PropReviewProjection propReviewProjection, String outcome) {
    String op =
        switch (type) {
          case SKU_AUDIT ->
              "create".equals(propReviewProjection.getActionType())
                  ? (propReviewProjection.getPreviousAuditId() == null
                      ? "create_sku"
                      : "resubmit_sku")
                  : "update".equals(propReviewProjection.getActionType())
                      ? "update_sku"
                      : "change_sku_status";
          case SN_ADJUSTMENT -> "create_sn_adjustment";
          case STOCK_ADJUSTMENT -> "create_stock_adjustment";
          default -> "create_qr_exception";
        };
    return result(
        op,
        outcome,
        propReviewProjection.getSkuId(),
        type,
        propReviewProjection.getId(),
        0,
        type.equals(QR_EXCEPTION) ? "reported" : CODE_PENDING,
        (type.equals(QR_EXCEPTION)
            ? propReviewProjection.getCreatedAt()
            : propReviewProjection.getSubmittedAt()));
  }

  /** 按用户和请求编号查找各类已登记的道具操作。 */
  public Found find(String actor, String key) {
    PropOperationRecordDO propOperationRecordDO =
        first(
            propOperationRecordMapper.selectPropOperationRecordListByActorAndRequest(
                actor, id(key)));
    if (propOperationRecordDO != null)
      return new Found(
          propOperationRecordDO.getTargetType(),
          propOperationRecordDO.getSkuId(),
          propOperationRecordDO.getRequestHash(),
          propJson.objectMapper.readValue(
              propOperationRecordDO.getResultJson(), CommandResultVO.class));
    for (String type : List.of(SKU_AUDIT, SN_ADJUSTMENT, STOCK_ADJUSTMENT, QR_EXCEPTION)) {
      PropReviewProjection propReviewProjection =
          switch (type) {
            case SKU_AUDIT ->
                PropReviewConverter.from(
                    first(propSkuAuditMapper.selectPropSkuAuditListByActorAndRequest(actor, key)));
            case SN_ADJUSTMENT ->
                PropReviewConverter.from(
                    first(
                        propSnAdjustmentAuditMapper
                            .selectPropSnAdjustmentAuditListByActorAndRequest(actor, key)));
            case STOCK_ADJUSTMENT ->
                PropReviewConverter.from(
                    first(
                        propStockAdjustmentAuditMapper
                            .selectPropStockAdjustmentAuditListByActorAndRequest(actor, key)));
            case QR_EXCEPTION ->
                PropReviewConverter.from(
                    first(
                        propQrExceptionRecordMapper
                            .selectPropQrExceptionRecordListByActorAndRequest(actor, key)));
            default -> throw new IllegalStateException("Unknown review source");
          };
      if (propReviewProjection != null)
        return new Found(
            type,
            propReviewProjection.getSkuId(),
            propReviewProjection.getRequestHash(),
            submitted(type, propReviewProjection, "replayed"));
    }
    return null;
  }

  /** 按用户与请求编号恢复结果，并拒绝同编号换内容。 */
  public CommandResultVO replay(String actor, String key, String hash) {
    PropRequests.Found found = find(actor, key);
    if (found == null) return null;
    if (!hash.equals(found.requestHash)) throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
    CommandResultVO commandResultVO = found.result;
    commandResultVO.setOutcome("replayed");
    return commandResultVO;
  }

  /** 校验权限后恢复指定请求已产生的业务结果。 */
  public CommandResultVO recover(String actor, String key, boolean admin) {
    PropRequests.Found found = find(actor, key);
    if (found == null) throw missing();
    if (admin != Set.of("issue_qr", "create_qr_exception").contains(found.result.getOperation()))
      throw missing();
    if (admin) propAccess.admin(actor, "prop-qr", "manage");
    else propAccess.own(actor, found.skuId);
    CommandResultVO commandResultVO = found.result;
    commandResultVO.setOutcome("replayed");
    return commandResultVO;
  }

  /** 保存操作摘要、快照和结果，供后续幂等回放。 */
  public void record(
      String actor,
      String key,
      String hash,
      String operation,
      CommandResultVO commandResultVO,
      Object before,
      Object after) {
    record(Ids.next(), actor, key, hash, operation, commandResultVO, before, after);
  }

  /** 保存操作摘要、快照和结果，供后续幂等回放。 */
  public void record(
      String operationId,
      String actor,
      String key,
      String hash,
      String operation,
      CommandResultVO commandResultVO,
      Object before,
      Object after) {
    OperationSnapshot operationSnapshot =
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.OperationSnapshot(
            1,
            operation,
            commandResultVO.getSkuId(),
            commandResultVO.getTargetType(),
            commandResultVO.getTargetId(),
            before);
    OperationSnapshot operationSnapshotAfter =
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.OperationSnapshot(
            1,
            operation,
            commandResultVO.getSkuId(),
            commandResultVO.getTargetType(),
            commandResultVO.getTargetId(),
            after);
    propOperationRecordMapper.insertPropOperationRecord(
        new PropOperationRecordDO()
            .id(operationId)
            .actorUserId(actor)
            .requestId(id(key))
            .requestHash(hash)
            .operationCode(operation)
            .skuId(commandResultVO.getSkuId())
            .targetType(commandResultVO.getTargetType())
            .targetId(commandResultVO.getTargetId())
            .beforeSnapshotJson(propJson.write(operationSnapshot))
            .afterSnapshotJson(propJson.write(operationSnapshotAfter))
            .resultJson(propJson.write(commandResultVO))
            .createdAt(now(clock)));
  }

  /** 根据已提交或既有待审申请构造操作结果。 */
  public CommandResultVO submitted(String type, PropSkuAuditDO propSkuAuditDO, String outcome) {
    return submitted(type, PropReviewConverter.from(propSkuAuditDO), outcome);
  }

  /** 根据已提交或既有待审申请构造操作结果。 */
  public CommandResultVO submitted(
      String type, PropSnAdjustmentAuditDO propSnAdjustmentAuditDO, String outcome) {
    return submitted(type, PropReviewConverter.from(propSnAdjustmentAuditDO), outcome);
  }

  /** 根据已提交或既有待审申请构造操作结果。 */
  public CommandResultVO submitted(
      String type, PropStockAdjustmentAuditDO propStockAdjustmentAuditDO, String outcome) {
    return submitted(type, PropReviewConverter.from(propStockAdjustmentAuditDO), outcome);
  }

  /** 根据已提交或既有待审申请构造操作结果。 */
  public CommandResultVO submitted(
      String type, PropQrExceptionRecordDO propQrExceptionRecordDO, String outcome) {
    return submitted(type, PropReviewConverter.from(propQrExceptionRecordDO), outcome);
  }

  /** 恢复原操作并核对调用方声明的操作类型。 */
  public CommandResultVO recover(String actor, String id, String operation, boolean admin) {
    CommandResultVO commandResultVO = recover(actor, id, admin);
    if ((operation != null) && !operation.equals(commandResultVO.getOperation()))
      throw com.mujian.backend.common.support.D2Values.missing();
    return commandResultVO;
  }
}
