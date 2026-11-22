package com.mujian.backend.prop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** 既有工单公共设施的固定输入边界，不扩展工单业务。 */
public final class PropCaseDTO {
  /** 构造当前数据对象，保留约定字段的类型和空值。 */
  private PropCaseDTO() {}

  public record CreateCase(
      @JsonProperty("id") String id,
      @JsonProperty("case_no") String caseNo,
      @JsonProperty("active_case_key") String activeCaseKey,
      @JsonProperty("case_type") String caseType,
      @JsonProperty("case_status") String caseStatus,
      @JsonProperty("priority") String priority,
      @JsonProperty("title") String title,
      @JsonProperty("buyer_user_id") String buyerUserId,
      @JsonProperty("factory_id") String factoryId,
      @JsonProperty("created_by_type") String createdByType,
      @JsonProperty("created_by_admin_user_id") String createdByAdminUserId,
      @JsonProperty("created_at") LocalDateTime createdAt,
      @JsonProperty("updated_at") LocalDateTime updatedAt) {}

  public record CreateRelation(
      @JsonProperty("id") String id,
      @JsonProperty("admin_case_id") String adminCaseId,
      @JsonProperty("relation_type") String relationType,
      @JsonProperty("relation_id") String relationId,
      @JsonProperty("relation_role") String relationRole,
      @JsonProperty("is_primary") Integer isPrimary,
      @JsonProperty("created_at") LocalDateTime createdAt) {}

  public record CreateAction(
      @JsonProperty("id") String id,
      @JsonProperty("action_no") String actionNo,
      @JsonProperty("admin_case_id") String adminCaseId,
      @JsonProperty("action_type") String actionType,
      @JsonProperty("action_status") String actionStatus,
      @JsonProperty("target_module") String targetModule,
      @JsonProperty("target_type") String targetType,
      @JsonProperty("target_id") String targetId,
      @JsonProperty("request_payload_json") String requestPayloadJson,
      @JsonProperty("result_payload_json") String resultPayloadJson,
      @JsonProperty("idempotency_key") String idempotencyKey,
      @JsonProperty("operator_admin_user_id") String operatorAdminUserId,
      @JsonProperty("operation_reason") String operationReason,
      @JsonProperty("operator_confirmed_at") LocalDateTime operatorConfirmedAt,
      @JsonProperty("executed_at") LocalDateTime executedAt,
      @JsonProperty("created_at") LocalDateTime createdAt,
      @JsonProperty("updated_at") LocalDateTime updatedAt) {}

  public record CloseCase(
      @JsonProperty("id") String id,
      @JsonProperty("version") Integer version,
      @JsonProperty("status") String status,
      @JsonProperty("summary") String summary,
      @JsonProperty("checklist") String checklist,
      @JsonProperty("now") LocalDateTime now,
      @JsonProperty("actor") String actor) {}

  public record CreateAudit(
      @JsonProperty("id") String id,
      @JsonProperty("audit_no") String auditNo,
      @JsonProperty("admin_user_id") String adminUserId,
      @JsonProperty("admin_case_id") String adminCaseId,
      @JsonProperty("case_action_id") String caseActionId,
      @JsonProperty("permission_code") String permissionCode,
      @JsonProperty("operation_code") String operationCode,
      @JsonProperty("operation_name") String operationName,
      @JsonProperty("target_module") String targetModule,
      @JsonProperty("target_type") String targetType,
      @JsonProperty("target_id") String targetId,
      @JsonProperty("request_id") String requestId,
      @JsonProperty("idempotency_key") String idempotencyKey,
      @JsonProperty("operation_reason") String operationReason,
      @JsonProperty("before_snapshot_json") String beforeSnapshotJson,
      @JsonProperty("after_snapshot_json") String afterSnapshotJson,
      @JsonProperty("result_status") String resultStatus,
      @JsonProperty("created_at") LocalDateTime createdAt) {}
}
