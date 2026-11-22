package com.mujian.backend.admin.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 后台操作及只读投影，敏感凭据只进入创建请求。 */
public final class Administration {
  private Administration() {}

  public record Account(
      String id,
      String userId,
      String username,
      boolean enabled,
      String roleCode,
      boolean identityEnabled,
      String lastLoginAt,
      String snapshotToken) {}

  public record Rule(
      String id,
      String ruleCode,
      String ruleTitle,
      String ruleVersion,
      String ruleScope,
      String contentType,
      String contentText,
      String contentUrl,
      boolean isRequired,
      boolean forceReaccept,
      String status,
      String snapshotToken) {}

  public record Menu(
      String id,
      String parentId,
      String menuName,
      String menuType,
      String permissionCode,
      String routePath,
      String componentPath,
      String iconName,
      boolean visible,
      boolean enabled,
      int sortNo,
      String snapshotToken) {}

  public record User(
      String userId,
      String nickname,
      String phoneMasked,
      String userStatus,
      String registrationStatus,
      String businessRole,
      String realnameStatus,
      String studentStatus,
      String createdAt) {}

  public record Audit(
      String id,
      String adminUserId,
      String operationCode,
      String permissionCode,
      String targetType,
      String targetId,
      String caseId,
      String resultStatus,
      String traceId,
      String createdAt) {}

  public record Grants(
      String roleId, String roleCode, List<String> menuIds, String snapshotToken) {}

  public record GrantInput(List<String> menuIds, String snapshotToken, String reason) {}

  public record Toggle(boolean enabled, String snapshotToken, String reason) {}

  public record Snapshot(String snapshotToken) {}

  public record NewAccount(String userId, String username, String password, String reason) {
    /** 输出该类型的安全摘要。 */
    @Override
    public String toString() {
      return "NewAccount[redacted]";
    }
  }

  public record Invalidation(String reason, String expectedStatus) {}

  public record Change(String field, String before, String after) {}

  public record AuditExtra(
      String reason, String resultMessage, String beforeJson, String afterJson) {}

  public record Totals(
      String pending, String waitingUser, String waitingFactory, String dueToday) {}

  public record CaseGroup(String caseType, String count) {}

  public record Alert(String reasonCode, String count) {}

  public record Attention(
      String caseId,
      String title,
      String sourceModule,
      String sourceId,
      String caseStatus,
      String sourceStatus,
      String deadlineAt) {}

  public record Dashboard(
      String asOf,
      String pending,
      String waitingUser,
      String waitingFactory,
      String dueToday,
      List<CaseGroup> caseGroups,
      List<Alert> channelAlerts,
      List<Attention> attentionItems) {}

  public record Usage(
      String usageId, String userCouponId, String checkoutBatchId, String paymentId) {}

  public record Allocation(
      String factoryId,
      String skuId,
      String rentalOrderId,
      int eligibleRentAmountFen,
      int allocatedDiscountAmountFen) {}

  public record AllocationAudit(
      String usageId,
      String userCouponId,
      String checkoutBatchId,
      List<Allocation> allocations,
      String paymentId) {}

  public record RuleInput(
      String ruleCode,
      String ruleTitle,
      String ruleVersion,
      String ruleScope,
      String contentType,
      String contentText,
      String contentUrl,
      boolean isRequired,
      boolean forceReaccept,
      String snapshotToken) {}

  public record MenuInput(
      String parentId,
      String menuName,
      String menuType,
      String permissionCode,
      String routePath,
      String componentPath,
      String iconName,
      boolean visible,
      boolean enabled,
      int sortNo,
      String snapshotToken) {}

  public record UserDetail(
      String userId,
      String nickname,
      String phoneMasked,
      String userStatus,
      String registrationStatus,
      String businessRole,
      String realnameStatus,
      String studentStatus,
      String createdAt,
      String studentValidUntil,
      List<String> ruleVersions) {}

  public record AuditDetail(
      String id,
      String adminUserId,
      String operationCode,
      String permissionCode,
      String targetType,
      String targetId,
      String caseId,
      String resultStatus,
      String traceId,
      String createdAt,
      String reason,
      String resultMessage,
      List<Change> changes) {}

  public record AccountFilter(Boolean enabled, String keyword) {}

  public record RuleFilter(String status, String ruleCode) {}

  public record UserFilter(String status, String keyword, String studentStatus) {}

  public record AuditFilter(
      String adminUserId,
      String targetModule,
      String resultStatus,
      LocalDateTime from,
      LocalDateTime until) {}
}
