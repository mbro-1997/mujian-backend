package com.mujian.backend.factory.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.domain.AdminCaseDO;
import com.mujian.backend.casework.domain.AdminCaseRelationDO;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.enums.FactoryReasonCode;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.common.support.*;
import com.mujian.backend.factory.mapper.FactoryInviteCodeMapper;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.projection.*;
import java.time.*;
import java.util.*;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FactoryCasework {
  private final FactoryReviewMapper factoryReviewMapper;
  private final FactoryInviteCodeMapper factoryInviteCodeMapper;
  private final AdminCaseMapper adminCaseMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;
  private final FactoryAccess factoryAccess;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(FactoryCasework.class);

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryCasework(
      FactoryReviewMapper factoryReviewMapper,
      FactoryInviteCodeMapper factoryInviteCodeMapper,
      AdminCaseMapper adminCaseMapper,
      AdminCaseRelationMapper adminCaseRelationMapper,
      FactoryAccess factoryAccess,
      PlatformTransactionManager platformTransactionManager,
      Clock clock) {
    this.factoryReviewMapper = factoryReviewMapper;
    this.factoryInviteCodeMapper = factoryInviteCodeMapper;
    this.adminCaseMapper = adminCaseMapper;
    this.adminCaseRelationMapper = adminCaseRelationMapper;
    this.factoryAccess = factoryAccess;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    this.clock = clock;
  }

  /** 调用者已按账号→工厂→邀请码→来源加锁。审批时必须成功；独立补建失败不回滚来源。 */
  public AdminCaseDO ensure(
      FactorySource factorySource, FactorySourceProjection factorySourceProjection) {
    List<AdminCaseDO> found =
        adminCaseMapper.selectAdminCasesBySource(
            factorySource.table, factorySourceProjection.getId(), true);
    if (!found.isEmpty()) {
      AdminCaseDO adminCaseDO = found.get(0);
      if (found.size() != 1
          || !factorySource.caseType.equals(adminCaseDO.getCaseType())
          || !factorySourceProjection.getApplicantUserId().equals(adminCaseDO.getBuyerUserId())
          || !CODE_PENDING.equals(adminCaseDO.getCaseStatus()))
        throw new com.mujian.backend.common.api.ApiFailure(
            500, CODE_DATA_INTEGRITY_ERROR, FactoryReasonCode.FACTORY_DATA_INCONSISTENT.name());
      return adminCaseDO;
    }
    String id = Ids.next();
    adminCaseMapper.insertAdminCase(
        new AdminCaseDO()
            .id(id)
            .caseNo("CASE" + id)
            .activeCaseKey(factorySource.table + ":" + factorySourceProjection.getId())
            .caseType(factorySource.caseType)
            .caseStatus(CODE_PENDING)
            .priority("P2")
            .title(factorySource == FactorySource.ONBOARDING ? "工厂入驻审核" : "工厂资料变更审核")
            .buyerUserId(factorySourceProjection.getApplicantUserId())
            .factoryId(
                factorySource == FactorySource.CHANGE
                    ? factorySourceProjection.getFactoryId()
                    : null)
            .createdByType(FulfillmentRole.system.name())
            .createdByAdminUserId(SystemIdentity.SYSTEM_USER_ID)
            .createdAt(now(clock))
            .updatedAt(now(clock)));
    adminCaseRelationMapper.insertAdminCaseRelation(
        new AdminCaseRelationDO()
            .id(Ids.next())
            .adminCaseId(id)
            .relationType(factorySource.table)
            .relationId(factorySourceProjection.getId())
            .relationRole("source")
            .isPrimary(1)
            .createdAt(now(clock)));
    return adminCaseMapper
        .selectAdminCasesBySource(factorySource.table, factorySourceProjection.getId(), true)
        .get(0);
  }

  /** 补齐业务已提交但工单尚未建立的记录。 */
  public void reconcile(FactorySource factorySource, String id) {
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            FactorySourceProjection factorySourceProjectionHint =
                factoryReviewMapper.selectFactorySourceById(factorySource, id, false);
            if (factorySourceProjectionHint == null) return;
            factoryAccess.lock(factorySourceProjectionHint.getApplicantUserId());
            if (factorySource == FactorySource.CHANGE)
              factoryReviewMapper.selectFactorySourceById(
                  FactorySource.PROFILE, factorySourceProjectionHint.getFactoryId(), true);
            else
              factoryInviteCodeMapper.selectFactoryInvitationById(
                  factorySourceProjectionHint.getInviteCodeId(), true);
            FactorySourceProjection factorySourceProjection =
                factoryReviewMapper.selectFactorySourceById(factorySource, id, true);
            if (CODE_PENDING.equals(factorySourceProjection.getAuditStatus()))
              ensure(factorySource, factorySourceProjection);
          });
    } catch (RuntimeException e) {
      LOG.warn(
          "event=factory_case_reconcile_failed sourceType={} sourceId={}", factorySource.table, id);
    }
  }

  /** 执行既有后台补偿任务并保持失败隔离。 */
  @Scheduled(fixedDelayString = "${mj.factory.case-reconcile-delay-ms:60000}")
  public void scheduled() {
    for (FactorySource factorySource : List.of(FactorySource.ONBOARDING, FactorySource.CHANGE)) {
      try {
        List<FactorySourceProjection> rows =
            factoryReviewMapper.selectFactorySourcesMissingCase(factorySource);
        rows.forEach(r -> reconcile(factorySource, r.getId()));
      } catch (RuntimeException e) {
        LOG.warn("event=factory_case_scan_failed sourceType={}", factorySource.table);
      }
    }
  }
}
