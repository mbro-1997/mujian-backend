package com.mujian.backend.prop.service;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.domain.AdminCaseActionDO;
import com.mujian.backend.casework.domain.AdminCaseDO;
import com.mujian.backend.casework.domain.AdminCaseRelationDO;
import com.mujian.backend.casework.domain.AdminOperationAuditDO;
import com.mujian.backend.casework.dto.CloseCaseDTO;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.prop.dto.PropCaseDTO.*;
import com.mujian.backend.prop.projection.PropCaseActionProjection;
import com.mujian.backend.prop.projection.PropCaseProjection;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 道具工单适配；使用公共工单的明确参数与结果，不复制SQL。 */
@Component
public class PropCaseGateway {
  private final AdminCaseMapper adminCaseMapper;
  private final AdminCaseActionMapper adminCaseActionMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;
  private final AdminOperationAuditMapper adminOperationAuditMapper;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropCaseGateway(
      AdminCaseMapper adminCaseMapper,
      AdminCaseActionMapper adminCaseActionMapper,
      AdminCaseRelationMapper adminCaseRelationMapper,
      AdminOperationAuditMapper adminOperationAuditMapper) {
    this.adminCaseMapper = adminCaseMapper;
    this.adminCaseActionMapper = adminCaseActionMapper;
    this.adminCaseRelationMapper = adminCaseRelationMapper;
    this.adminOperationAuditMapper = adminOperationAuditMapper;
  }

  /** 通过既有工单能力读取来源关联的工单。 */
  public List<PropCaseProjection> cases(String relationType, String relationId, boolean lock) {
    List<PropCaseProjection> results = new ArrayList<>();
    for (AdminCaseDO adminCaseDO :
        adminCaseMapper.selectAdminCasesBySource(relationType, relationId, lock))
      results.add(
          new PropCaseProjection(
              adminCaseDO.getId(),
              adminCaseDO.getCaseType(),
              adminCaseDO.getCaseStatus(),
              adminCaseDO.getVersion()));
    return results;
  }

  /** 按稳定请求编号恢复已执行的审核动作。 */
  public List<PropCaseActionProjection> action(String key) {
    List<PropCaseActionProjection> results = new ArrayList<>();
    for (AdminCaseActionDO adminCaseActionDO :
        adminCaseActionMapper.selectAdminCaseActionsByIdempotencyKey(key))
      results.add(
          new PropCaseActionProjection(
              adminCaseActionDO.getRequestPayloadJson(), adminCaseActionDO.getResultPayloadJson()));
    return results;
  }

  /** 保存工单基础信息，不复制公共工单SQL。 */
  public void insertCase(CreateCase createCase) {
    adminCaseMapper.insertAdminCase(
        new AdminCaseDO()
            .id(createCase.id())
            .caseNo(createCase.caseNo())
            .activeCaseKey(createCase.activeCaseKey())
            .caseType(createCase.caseType())
            .caseStatus(createCase.caseStatus())
            .priority(createCase.priority())
            .title(createCase.title())
            .buyerUserId(createCase.buyerUserId())
            .factoryId(createCase.factoryId())
            .createdByType(createCase.createdByType())
            .createdByAdminUserId(createCase.createdByAdminUserId())
            .createdAt(createCase.createdAt())
            .updatedAt(createCase.updatedAt()));
  }

  /** 登记工单与道具业务来源之间的关联。 */
  public void insertRelation(CreateRelation createRelation) {
    adminCaseRelationMapper.insertAdminCaseRelation(
        new AdminCaseRelationDO()
            .id(createRelation.id())
            .adminCaseId(createRelation.adminCaseId())
            .relationType(createRelation.relationType())
            .relationId(createRelation.relationId())
            .relationRole(createRelation.relationRole())
            .isPrimary(createRelation.isPrimary())
            .createdAt(createRelation.createdAt()));
  }

  /** 保存审核动作及其请求与结果快照。 */
  public void insertAction(CreateAction createAction) {
    adminCaseActionMapper.insertAdminCaseAction(
        new AdminCaseActionDO()
            .id(createAction.id())
            .actionNo(createAction.actionNo())
            .adminCaseId(createAction.adminCaseId())
            .actionType(createAction.actionType())
            .actionStatus(createAction.actionStatus())
            .targetModule(createAction.targetModule())
            .targetType(createAction.targetType())
            .targetId(createAction.targetId())
            .requestPayloadJson(createAction.requestPayloadJson())
            .resultPayloadJson(createAction.resultPayloadJson())
            .idempotencyKey(createAction.idempotencyKey())
            .operatorAdminUserId(createAction.operatorAdminUserId())
            .operationReason(createAction.operationReason())
            .operatorConfirmedAt(createAction.operatorConfirmedAt())
            .executedAt(createAction.executedAt())
            .createdAt(createAction.createdAt())
            .updatedAt(createAction.updatedAt()));
  }

  /** 通过既有工单能力关闭当前来源工单。 */
  public int close(CloseCase closeCase) {
    return adminCaseMapper.closeAdminCaseByVersion(
        new CloseCaseDTO()
            .id(closeCase.id())
            .version(closeCase.version())
            .status(closeCase.status())
            .summary(closeCase.summary())
            .checklist(closeCase.checklist())
            .now(closeCase.now())
            .actor(closeCase.actor()));
  }

  /** 通过公共审计能力保存操作留痕。 */
  public void insertAudit(CreateAudit createAudit) {
    adminOperationAuditMapper.insertAdminOperationAudit(
        new AdminOperationAuditDO()
            .id(createAudit.id())
            .auditNo(createAudit.auditNo())
            .adminUserId(createAudit.adminUserId())
            .adminCaseId(createAudit.adminCaseId())
            .caseActionId(createAudit.caseActionId())
            .permissionCode(createAudit.permissionCode())
            .operationCode(createAudit.operationCode())
            .operationName(createAudit.operationName())
            .targetModule(createAudit.targetModule())
            .targetType(createAudit.targetType())
            .targetId(createAudit.targetId())
            .requestId(createAudit.requestId())
            .idempotencyKey(createAudit.idempotencyKey())
            .operationReason(createAudit.operationReason())
            .beforeSnapshotJson(createAudit.beforeSnapshotJson())
            .afterSnapshotJson(createAudit.afterSnapshotJson())
            .resultStatus(createAudit.resultStatus())
            .createdAt(createAudit.createdAt()));
  }
}
