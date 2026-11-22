package com.mujian.backend.prop.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.PropReviewSources.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.prop.domain.PropQrExceptionRecordDO;
import com.mujian.backend.prop.domain.PropSkuAuditDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSnAdjustmentAuditDO;
import com.mujian.backend.prop.domain.PropStockAdjustmentAuditDO;
import com.mujian.backend.prop.mapper.PropQrExceptionRecordMapper;
import com.mujian.backend.prop.mapper.PropReviewMapper;
import com.mujian.backend.prop.mapper.PropSkuAuditMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentAuditMapper;
import com.mujian.backend.prop.mapper.PropStockAdjustmentAuditMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.projection.PropCaseProjection;
import com.mujian.backend.prop.projection.PropFactoryProjection;
import com.mujian.backend.prop.projection.PropReviewProjection;
import com.mujian.backend.prop.utils.PropJson;
import java.time.Clock;
import java.util.*;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PropCasework {
  private final PropQrExceptionRecordMapper propQrExceptionRecordMapper;
  private final PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper;
  private final PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper;
  private final PropSkuAuditMapper propSkuAuditMapper;
  private final PropReviewMapper propReviewMapper;
  private final PropCaseGateway propCaseGateway;
  private final PropAccess propAccess;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final PropJson propJson;
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PropCasework.class);

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropCasework(
      PropQrExceptionRecordMapper propQrExceptionRecordMapper,
      PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper,
      PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper,
      PropSkuAuditMapper propSkuAuditMapper,
      PropReviewMapper propReviewMapper,
      PropCaseGateway propCaseGateway,
      PropAccess propAccess,
      PlatformTransactionManager platformTransactionManager,
      Clock clock,
      PropJson propJson) {
    this.propQrExceptionRecordMapper = propQrExceptionRecordMapper;
    this.propStockAdjustmentAuditMapper = propStockAdjustmentAuditMapper;
    this.propSnAdjustmentAuditMapper = propSnAdjustmentAuditMapper;
    this.propSkuAuditMapper = propSkuAuditMapper;
    this.propReviewMapper = propReviewMapper;
    this.propCaseGateway = propCaseGateway;
    this.propAccess = propAccess;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    transactionTemplate.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    transactionTemplate.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
    this.propJson = propJson;
  }

  /** 按稳定来源创建或复用唯一审核工单。 */
  public com.mujian.backend.prop.projection.PropCaseProjection ensure(
      String type, PropReviewProjection propReviewProjection, PropSkuDO propSkuDO) {
    String table = PropAccess.table(type),
        caseType =
            type.equals(QR_EXCEPTION)
                ? QR_EXCEPTION
                : type.equals(SKU_AUDIT)
                    ? (Set.of("create", "update").contains(propReviewProjection.getActionType())
                        ? "prop_sku_review"
                        : "prop_sku_status_review")
                    : "stock_adjustment_review";
    List<PropCaseProjection> propCaseProjectionList =
        propCaseGateway.cases(table, propReviewProjection.getId(), true);
    if (!propCaseProjectionList.isEmpty()) {
      if (propCaseProjectionList.size() != 1
          || !caseType.equals(propCaseProjectionList.get(0).caseType())
          || !CODE_PENDING.equals(propCaseProjectionList.get(0).caseStatus()))
        throw PropAccess.inconsistent();
      return propCaseProjectionList.get(0);
    }
    String id = Ids.next();
    propCaseGateway.insertCase(
        new com.mujian.backend.prop.dto.PropCaseDTO.CreateCase(
            id,
            "CASE" + id,
            table + ":" + propReviewProjection.getId(),
            caseType,
            CODE_PENDING,
            "P2",
            type.equals(QR_EXCEPTION) ? "二维码异常审核" : "道具与库存审核",
            type.equals(QR_EXCEPTION) ? null : propReviewProjection.getApplicantUserId(),
            propSkuDO.getFactoryId(),
            FulfillmentRole.system.name(),
            SystemIdentity.SYSTEM_USER_ID,
            now(clock),
            now(clock)));
    propCaseGateway.insertRelation(
        new com.mujian.backend.prop.dto.PropCaseDTO.CreateRelation(
            Ids.next(), id, table, propReviewProjection.getId(), "source", 1, now(clock)));
    return propCaseGateway.cases(table, propReviewProjection.getId(), true).get(0);
  }

  /** 读取业务来源关联的审核工单标识。 */
  public String caseId(String type, String id) {
    List<PropCaseProjection> propCaseProjectionList =
        propCaseGateway.cases(PropAccess.table(type), id, false);
    return propCaseProjectionList.isEmpty() ? null : propCaseProjectionList.get(0).id();
  }

  /** 撤回申请对应工单并保留来源关联。 */
  public void cancel(String type, PropReviewProjection propReviewProjection, String reason) {
    // A factory withdrawal is not an administrator decision: the direct-operation record is its
    // audit.
    for (PropCaseProjection propCaseProjection :
        propCaseGateway.cases(PropAccess.table(type), propReviewProjection.getId(), true))
      propReviewMapper.updatePropCaseForCancelledSource(
          SystemIdentity.SYSTEM_USER_ID,
          propCaseProjection.id(),
          reason,
          now(clock),
          propJson.write(
              new com.mujian.backend.prop.dto.PropAuditFactsDTO.CaseState(
                  1, type, propReviewProjection.getId(), "cancelled")));
  }

  /** 登记提交后补建工单动作，避免工单失败回滚原申请。 */
  public void defer(String type, String id) {
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              /** 在原事务提交后补建关联审核工单。 */
              @Override
              public void afterCommit() {
                reconcile(type, id);
              }
            });
  }

  /** 补齐业务申请与审核工单的关联。 */
  public void reconcile(String type, String id) {
    try {
      transactionTemplate.executeWithoutResult(
          transactionStatus -> {
            PropReviewProjection propReviewProjection = propAccess.review(type, id, false);
            if (propReviewProjection == null) return;
            PropSkuDO propSkuDO = propAccess.sku(propReviewProjection.getSkuId());
            PropFactoryProjection propFactoryProjection =
                propAccess.factoryById(propSkuDO.getFactoryId(), false);
            propAccess.lock(propFactoryProjection.getOwnerUserId(), propSkuDO.getId(), false);
            PropReviewProjection propReviewProjectionLocked = propAccess.review(type, id, true);
            if ((type.equals(QR_EXCEPTION) ? "reported" : CODE_PENDING)
                .equals(
                    (type.equals(QR_EXCEPTION)
                        ? propReviewProjectionLocked.getExceptionStatus()
                        : propReviewProjectionLocked.getAuditStatus())))
              ensure(type, propReviewProjectionLocked, propSkuDO);
          });
    } catch (RuntimeException runtimeException) {
      LOG.warn("event=prop_case_reconcile_failed type={} id={}", type, id);
    }
  }

  /** 定时补齐尚未建立关联的道具审核工单。 */
  @Scheduled(fixedDelayString = "${mj.prop.case-reconcile-delay-ms:60000}")
  public void scheduled() {
    for (String type : List.of(SKU_AUDIT, SN_ADJUSTMENT, STOCK_ADJUSTMENT, QR_EXCEPTION)) {
      try {
        List<String> ids = new ArrayList<>();
        switch (type) {
          case SKU_AUDIT -> {
            for (PropSkuAuditDO propSkuAuditDO :
                propSkuAuditMapper.selectUnlinkedPendingPropSkuAuditList())
              ids.add(propSkuAuditDO.getId());
          }
          case SN_ADJUSTMENT -> {
            for (PropSnAdjustmentAuditDO propSnAdjustmentAuditDO :
                propSnAdjustmentAuditMapper.selectUnlinkedPendingPropSnAdjustmentAuditList())
              ids.add(propSnAdjustmentAuditDO.getId());
          }
          case STOCK_ADJUSTMENT -> {
            for (PropStockAdjustmentAuditDO propStockAdjustmentAuditDO :
                propStockAdjustmentAuditMapper.selectUnlinkedPendingPropStockAdjustmentAuditList())
              ids.add(propStockAdjustmentAuditDO.getId());
          }
          case QR_EXCEPTION -> {
            for (PropQrExceptionRecordDO propQrExceptionRecordDO :
                propQrExceptionRecordMapper.selectUnlinkedPendingPropQrExceptionRecordList())
              ids.add(propQrExceptionRecordDO.getId());
          }
          default -> throw new IllegalStateException("Unknown review source");
        }
        for (String sourceId : ids) reconcile(type, sourceId);
      } catch (RuntimeException runtimeException) {
        LOG.warn("event=prop_case_scan_failed type={}", type);
      }
    }
  }
}
