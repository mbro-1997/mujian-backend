package com.mujian.backend.prop.service.impl;

import com.mujian.backend.common.enums.FulfillmentRole;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.PropReviewSources.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.PropReasonCode;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSkuStockDO;
import com.mujian.backend.prop.domain.PropSnAdjustmentItemDO;
import com.mujian.backend.prop.domain.PropSnInstanceDO;
import com.mujian.backend.prop.dto.PropAuditFactsDTO.ReviewRequest;
import com.mujian.backend.prop.dto.PropSkuFieldsDTO;
import com.mujian.backend.prop.dto.PropWriteDTO.DecideReview;
import com.mujian.backend.prop.dto.ReviewDecisionDTO;
import com.mujian.backend.prop.mapper.PropQrCodeMapper;
import com.mujian.backend.prop.mapper.PropQrExceptionRecordMapper;
import com.mujian.backend.prop.mapper.PropSkuAuditMapper;
import com.mujian.backend.prop.mapper.PropSkuMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentAuditMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentItemMapper;
import com.mujian.backend.prop.mapper.PropSnInstanceMapper;
import com.mujian.backend.prop.mapper.PropStockAdjustmentAuditMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.projection.PropCaseActionProjection;
import com.mujian.backend.prop.projection.PropCaseProjection;
import com.mujian.backend.prop.projection.PropFactoryProjection;
import com.mujian.backend.prop.projection.PropReviewProjection;
import com.mujian.backend.prop.projection.PropSnPendingProjection;
import com.mujian.backend.prop.service.PropAudit;
import com.mujian.backend.prop.service.PropCaseGateway;
import com.mujian.backend.prop.service.PropCasework;
import com.mujian.backend.prop.service.PropInventory;
import com.mujian.backend.prop.service.PropReviewService;
import com.mujian.backend.prop.service.PropSnapshots;
import com.mujian.backend.prop.utils.PropJson;
import com.mujian.backend.prop.vo.ReviewResultVO;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PropReviewServiceImpl implements PropReviewService {
  private final PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper;
  private final PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper;
  private final PropSkuAuditMapper propSkuAuditMapper;
  private final PropQrExceptionRecordMapper propQrExceptionRecordMapper;
  private final PropSkuMapper propSkuMapper;
  private final PropQrCodeMapper propQrCodeMapper;
  private final PropSnAdjustmentItemMapper propSnAdjustmentItemMapper;
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final PropAccess propAccess;
  private final PropInventory propInventory;
  private final PropSnapshots propSnapshots;
  private final PropCasework propCasework;
  private final PropCaseGateway propCaseGateway;
  private final PropAudit propAudit;
  private final PropJson propJson;
  private final Clock clock;
  private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropReviewServiceImpl(
      PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper,
      PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper,
      PropSkuAuditMapper propSkuAuditMapper,
      PropQrExceptionRecordMapper propQrExceptionRecordMapper,
      PropSkuMapper propSkuMapper,
      PropQrCodeMapper propQrCodeMapper,
      PropSnAdjustmentItemMapper propSnAdjustmentItemMapper,
      PropSnInstanceMapper propSnInstanceMapper,
      PropAccess propAccess,
      PropInventory propInventory,
      PropSnapshots propSnapshots,
      PropCasework propCasework,
      PropCaseGateway propCaseGateway,
      PropAudit propAudit,
      PropJson propJson,
      Clock clock,
      org.springframework.transaction.PlatformTransactionManager platformTransactionManager) {
    transactionTemplate =
        new org.springframework.transaction.support.TransactionTemplate(platformTransactionManager);
    transactionTemplate.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.propStockAdjustmentAuditMapper = propStockAdjustmentAuditMapper;
    this.propSnAdjustmentAuditMapper = propSnAdjustmentAuditMapper;
    this.propSkuAuditMapper = propSkuAuditMapper;
    this.propQrExceptionRecordMapper = propQrExceptionRecordMapper;
    this.propSkuMapper = propSkuMapper;
    this.propQrCodeMapper = propQrCodeMapper;
    this.propSnAdjustmentItemMapper = propSnAdjustmentItemMapper;
    this.propSnInstanceMapper = propSnInstanceMapper;
    this.propAccess = propAccess;
    this.propInventory = propInventory;
    this.propSnapshots = propSnapshots;
    this.propCasework = propCasework;
    this.propCaseGateway = propCaseGateway;
    this.propAudit = propAudit;
    this.propJson = propJson;
    this.clock = clock;
  }

  /** 校验权限后恢复指定请求已产生的业务结果。 */
  public ReviewResultVO recover(String actor, String key) {
    PropCaseActionProjection propCaseActionProjection =
        com.mujian.backend.infrastructure.persistence.MappingInputs.first(
            propCaseGateway.action("prop-review:" + actor + ":" + id(key)));
    if (propCaseActionProjection == null) throw missing();
    ReviewRequest reviewRequest =
        propJson.objectMapper.readValue(
            propCaseActionProjection.requestPayloadJson(),
            com.mujian.backend.prop.dto.PropAuditFactsDTO.ReviewRequest.class);
    propAccess.admin(
        actor,
        reviewRequest.sourceType().equals(QR_EXCEPTION) ? "prop-qr" : "prop-review",
        "review");
    return propJson.objectMapper.readValue(
        propCaseActionProjection.resultPayloadJson(), ReviewResultVO.class);
  }

  /** 预览与锁内批准共用业务条件，驳回可独立执行。 */
  public void approvable(
      String type, PropReviewProjection propReviewProjection, PropSkuDO propSkuDO) {
    // Revoking a risky code is administrative containment, even when its owner cannot operate.
    // The same rule is used by the read capability and the locked approval path.
    if (type.equals(QR_EXCEPTION)) {
      PropQrCodeDO propQrCodeDO =
          propQrCodeMapper.selectPropQrCode(propReviewProjection.getOriginalQrCodeId(), false);
      com.mujian.backend.prop.policy.PropQrReviewPolicy.assertApprovable(
          propReviewProjection, propSkuDO, propQrCodeDO);
      return;
    }
    PropFactoryProjection propFactoryProjection =
        propAccess.factoryById(propSkuDO.getFactoryId(), false);
    propAccess.factoryAccess.context(propFactoryProjection.getOwnerUserId(), true);
    propAccess.operating(propFactoryProjection);
    if (type.equals(SKU_AUDIT)) {
      propAccess.version(propSkuDO.getVersion(), propReviewProjection.getBaseSkuVersion());
      if (!Objects.equals(propSkuDO.getSkuStatus(), propReviewProjection.getBeforeSkuStatus()))
        throw conflict(PropReasonCode.SKU_STATE_CONFLICT.name());
      if (!Set.of("create", "up", "down", "update").contains(propReviewProjection.getActionType()))
        throw conflict(PropReasonCode.SKU_STATE_CONFLICT.name());
      if ("up".equals(propReviewProjection.getActionType()))
        propSnapshots.verifyApproval(propSnapshots.current(propSkuDO));
      return;
    }
    propAccess.business(propFactoryProjection.getOwnerUserId(), propSkuDO);
    if (type.equals(SN_ADJUSTMENT)) {
      List<PropSnAdjustmentItemDO> propSnAdjustmentItemDOList =
          propSnAdjustmentItemMapper.selectApprovalSnAdjustmentItemListByAuditId(
              propReviewProjection.getId());
      List<String> snIds = new ArrayList<>();
      for (PropSnAdjustmentItemDO propSnAdjustmentItemDO : propSnAdjustmentItemDOList)
        snIds.add(propSnAdjustmentItemDO.getSnId());
      Map<String, PropSnInstanceDO> propSnInstanceDOMap = new HashMap<>();
      Map<String, String> pendingAuditIds = new HashMap<>();
      // 原写入口已按原顺序锁定实物；这里只批量读取核验材料，不改变锁及逐项错误优先级。
      for (int start = 0; start < snIds.size(); start += 500) {
        List<String> batchIds = snIds.subList(start, Math.min(start + 500, snIds.size()));
        for (PropSnInstanceDO propSnInstanceDO :
            propSnInstanceMapper.selectPropSnInstanceListByIds(batchIds))
          propSnInstanceDOMap.put(propSnInstanceDO.getId(), propSnInstanceDO);
        for (PropSnPendingProjection propSnPendingProjection :
            propSnAdjustmentAuditMapper.selectPendingSnAdjustmentListBySnIds(batchIds))
          pendingAuditIds.putIfAbsent(
              propSnPendingProjection.snId(), propSnPendingProjection.auditId());
      }
      for (PropSnAdjustmentItemDO propSnAdjustmentItemDO : propSnAdjustmentItemDOList) {
        PropSnInstanceDO propSnInstanceDO =
            propSnInstanceDOMap.get(propSnAdjustmentItemDO.getSnId());
        if (propSnInstanceDO == null) throw missing();
        if (!Objects.equals(
            propSnAdjustmentItemDO.getBeforeInstanceStatus(), propSnInstanceDO.getInstanceStatus()))
          throw conflict(PropReasonCode.SN_STATE_CONFLICT.name());
        if (!propReviewProjection.getId().equals(pendingAuditIds.get(propSnInstanceDO.getId())))
          throw conflict(PropReasonCode.SN_ADJUSTMENT_PENDING.name());
      }
    } else {
      PropSkuStockDO propSkuStockDO = propInventory.stock(propSkuDO.getId(), false);
      propInventory.assertReduction(propSkuStockDO, propReviewProjection.getQuantityDelta());
      for (int count :
          List.of(
              propSkuStockDO.getTotalQuantity(),
              propSkuStockDO.getRentableBaseQuantity(),
              propSkuStockDO.getAvailableQuantity()))
        if ((long) count + propReviewProjection.getQuantityDelta() > Integer.MAX_VALUE)
          throw conflict(PropReasonCode.STOCK_NOT_ENOUGH.name());
    }
  }

  /** 按原锁序处理审核决定，并统一提交业务变更和审计。 */
  public ReviewResultVO decide(
      String actor,
      String type,
      String auditId,
      String key,
      boolean approve,
      ReviewDecisionDTO reviewDecisionDTO,
      String trace) {
    propAccess.admin(actor, type.equals(QR_EXCEPTION) ? "prop-qr" : "prop-review", "review");
    PropCaseActionProjection propCaseActionProjection =
        com.mujian.backend.infrastructure.persistence.MappingInputs.first(
            propCaseGateway.action("prop-review:" + actor + ":" + id(key)));
    if (propCaseActionProjection != null) {
      String expected =
          propJson.hash((approve ? "approve:" : "reject:") + type, auditId, reviewDecisionDTO);
      if (!expected.equals(
          propJson
              .objectMapper
              .readValue(
                  propCaseActionProjection.requestPayloadJson(),
                  com.mujian.backend.prop.dto.PropAuditFactsDTO.ReviewRequest.class)
              .requestHash())) throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
      return propJson.objectMapper.readValue(
          propCaseActionProjection.resultPayloadJson(), ReviewResultVO.class);
    }
    // Immutable source materials are checked before taking business locks. Replay never revalidates
    // TTL.
    PropReviewProjection propReviewProjection = propAccess.source(actor, type, auditId, true);
    if (approve
        && type.equals(SKU_AUDIT)
        && Set.of("create", "update").contains(propReviewProjection.getActionType())
        && CODE_PENDING.equals(propReviewProjection.getAuditStatus()))
      propSnapshots.verifyMaterialApproval(
          propSnapshots.fields(propReviewProjection.getProposedSnapshotJson()));
    return transactionTemplate.execute(
        transactionStatus -> {
          String domain = type.equals(QR_EXCEPTION) ? "prop-qr" : "prop-review";
          propAccess.admin(actor, domain, "review");
          PropReviewProjection propReviewProjectionHint =
              propAccess.source(actor, type, auditId, true);
          PropSkuDO propSkuDO = propAccess.lock(actor, propReviewProjectionHint.getSkuId(), true);
          String
              hash =
                  propJson.hash(
                      (approve ? "approve:" : "reject:") + type, auditId, reviewDecisionDTO),
              actionKey = "prop-review:" + actor + ":" + id(key);
          PropCaseActionProjection propCaseActionProjectionOld =
              com.mujian.backend.infrastructure.persistence.MappingInputs.first(
                  propCaseGateway.action(actionKey));
          if (propCaseActionProjectionOld != null) {
            if (!hash.equals(
                propJson
                    .objectMapper
                    .readValue(
                        propCaseActionProjectionOld.requestPayloadJson(),
                        com.mujian.backend.prop.dto.PropAuditFactsDTO.ReviewRequest.class)
                    .requestHash())) throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
            return propJson.objectMapper.readValue(
                propCaseActionProjectionOld.resultPayloadJson(), ReviewResultVO.class);
          }
          if (type.equals(SN_ADJUSTMENT)) propInventory.lockItems(auditId);
          if (type.equals(STOCK_ADJUSTMENT)) propInventory.stock(propSkuDO.getId(), true);
          if (type.equals(QR_EXCEPTION) && propReviewProjectionHint.getSnId() != null)
            propInventory.sn(propReviewProjectionHint.getSnId(), true);
          PropReviewProjection propReviewProjectionLocked = propAccess.review(type, auditId, true);
          boolean qr = type.equals(QR_EXCEPTION);
          if (!(qr ? "reported" : CODE_PENDING)
              .equals(
                  (qr
                      ? propReviewProjectionLocked.getExceptionStatus()
                      : propReviewProjectionLocked.getAuditStatus())))
            throw conflict(PropReasonCode.PROP_ALREADY_REVIEWED.name());
          propAccess.version(
              propReviewProjectionLocked.getVersion(), reviewDecisionDTO.expectedVersion());
          if (qr)
            propQrCodeMapper.selectPropQrCode(
                propReviewProjectionLocked.getOriginalQrCodeId(), true);
          String reason = text(reviewDecisionDTO.reason(), 255);
          if (approve) approvable(type, propReviewProjectionLocked, propSkuDO);
          PropCaseProjection propCaseProjection =
              propCasework.ensure(type, propReviewProjectionLocked, propSkuDO);
          String state =
              qr ? (approve ? "resolved" : "cancelled") : (approve ? CODE_APPROVED : CODE_REJECTED);
          if (qr) {
            if (approve) {
              PropQrCodeDO propQrCodeDO =
                  propQrCodeMapper.selectPropQrCode(
                      propReviewProjectionLocked.getOriginalQrCodeId(), false);
              propQrCodeMapper.updatePropQrRevocation(
                  new com.mujian.backend.prop.dto.PropWriteDTO.RevokeQr(
                      propQrCodeDO.getId(),
                      "revoked",
                      actor,
                      now(clock),
                      propQrCodeDO.getVersion() + 1,
                      null,
                      now(clock)));
            }
            propQrExceptionRecordMapper.updateQrExceptionResolution(
                new com.mujian.backend.prop.dto.PropWriteDTO.ResolveQrException(
                    auditId,
                    state,
                    actor,
                    approve ? "disable_only" : null,
                    reason,
                    now(clock),
                    propReviewProjectionLocked.getVersion() + 1,
                    now(clock)));
          } else {
            DecideReview decideReview =
                new com.mujian.backend.prop.dto.PropWriteDTO.DecideReview(
                    auditId,
                    state,
                    actor,
                    reason,
                    approve ? null : reason,
                    now(clock),
                    propReviewProjectionLocked.getVersion() + 1,
                    now(clock));
            switch (type) {
              case SKU_AUDIT -> propSkuAuditMapper.updateSkuAuditDecision(decideReview);
              case SN_ADJUSTMENT ->
                  propSnAdjustmentAuditMapper.updateSnAdjustmentDecision(decideReview);
              case STOCK_ADJUSTMENT ->
                  propStockAdjustmentAuditMapper.updateStockAdjustmentDecision(decideReview);
              default -> throw ApiFailure.invalid();
            }
          }
          if (approve && !qr) {
            if (type.equals(SKU_AUDIT)) {
              if (Set.of("create", "update").contains(propReviewProjectionLocked.getActionType())) {
                PropSkuFieldsDTO propSkuFieldsDTO =
                    propSnapshots.fields(propReviewProjectionLocked.getProposedSnapshotJson());
                propSnapshots.verifyApproval(propSkuFieldsDTO);
                propSnapshots.writeFields(propSkuDO.getId(), propSkuFieldsDTO);
                if ("create".equals(propReviewProjectionLocked.getActionType()))
                  propInventory.initial(
                      propSkuDO, propSkuAuditMapper.selectPropSkuAudit(auditId, false), actor);
              }
              propSkuMapper.updatePropSkuStatusByVersion(
                  new com.mujian.backend.prop.dto.PropWriteDTO.ChangeSkuStatus(
                      propSkuDO.getId(),
                      propReviewProjectionLocked.getTargetSkuStatus(),
                      propSkuDO.getVersion() + 1,
                      now(clock)));
            } else if (type.equals(SN_ADJUSTMENT)) {
              if ("increase".equals(propReviewProjectionLocked.getAdjustmentType()))
                propInventory.increaseSn(propReviewProjectionLocked, actor);
              else
                for (PropSnAdjustmentItemDO propSnAdjustmentItemDO :
                    propSnAdjustmentItemMapper.selectReviewedSnAdjustmentItemListByAuditId(auditId))
                  propInventory.transition(
                      propInventory.sn(propSnAdjustmentItemDO.getSnId(), false),
                      propSnAdjustmentItemDO.getTargetInstanceStatus(),
                      actor,
                      FulfillmentRole.admin.name(),
                      "manual_adjustment",
                      propReviewProjectionLocked.getApplyRemark(),
                      auditId,
                      "sn-adjust:" + auditId + ":" + propSnAdjustmentItemDO.getSnId());
            } else propInventory.changeStock(propReviewProjectionLocked, actor, false);
          }
          ReviewResultVO reviewResultVO =
              new ReviewResultVO()
                  .sourceType(type)
                  .sourceId(auditId)
                  .skuId(propSkuDO.getId())
                  .decision(approve ? "approve" : "reject")
                  .status(state)
                  .version(propReviewProjectionLocked.getVersion() + 1)
                  .reviewedAt(time(now(clock)));
          String action = Ids.next(),
              operation =
                  (approve ? "approve_prop_" : "reject_prop_")
                      + switch (type) {
                        case SKU_AUDIT -> "sku";
                        case SN_ADJUSTMENT -> "sn";
                        case STOCK_ADJUSTMENT -> "stock";
                        default -> QR_EXCEPTION;
                      };
          propCaseGateway.insertAction(
              new com.mujian.backend.prop.dto.PropCaseDTO.CreateAction(
                  action,
                  "ACT" + action,
                  propCaseProjection.id(),
                  operation,
                  "succeeded",
                  "02",
                  PropAccess.table(type),
                  auditId,
                  propJson.write(
                      new com.mujian.backend.prop.dto.PropAuditFactsDTO.ReviewRequest(
                          1,
                          type,
                          auditId,
                          approve ? "approve" : "reject",
                          hash,
                          reviewDecisionDTO.expectedVersion(),
                          reason)),
                  propJson.write(reviewResultVO),
                  actionKey,
                  actor,
                  reason,
                  now(clock),
                  now(clock),
                  now(clock),
                  now(clock)));
          if (propCaseGateway.close(
                  new com.mujian.backend.prop.dto.PropCaseDTO.CloseCase(
                      propCaseProjection.id(),
                      propCaseProjection.version(),
                      state,
                      reason,
                      propJson.write(
                          new com.mujian.backend.prop.dto.PropAuditFactsDTO.CaseOutcome(
                              1, type, auditId, state, action)),
                      now(clock),
                      actor))
              != 1) throw conflict(CODE_VERSION_CONFLICT);
          propAudit.record(
              actor,
              PropAccess.table(type),
              auditId,
              operation,
              "admin:" + domain + ":review",
              trace,
              actionKey,
              propCaseProjection.id(),
              action,
              reason,
              new com.mujian.backend.prop.dto.PropAuditFactsDTO.StatusVersion(
                  qr ? "reported" : CODE_PENDING, propReviewProjectionLocked.getVersion()),
              reviewResultVO);
          return reviewResultVO;
        });
  }
}
