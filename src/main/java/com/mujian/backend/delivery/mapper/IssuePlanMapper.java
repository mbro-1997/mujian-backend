package com.mujian.backend.delivery.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IssuePlanMapper {
  record Action(String id,String caseId,String issueId,String orderId,String status,String payload,
      Integer rent,Integer deposit,Integer freight,int schemeVersion,boolean locked) {}
  record Source(String suborderId,int rent,int deposit,int freight,int originalRent,int originalDeposit,int originalFreight) {}
  record Refund(String refundId,String status,int amountFen) {}
  record Offsets(int rent,int deposit,int freight) {}

  /** 按 id 查询 admin_case_action 记录。 */
  Action actionById(@Param("id") String id);
  /** 按 orderId, issueId, terminateOnly 查询 admin_case_action 记录。 */
  Action selectActive(@Param("orderId") String orderId,@Param("issueId") String issueId,@Param("terminateOnly") boolean terminateOnly);
  /** 按 orderId, excludeAction 查询 payment_allocation 记录。 */
  List<Source> selectRefundFundingSourcesByOrder(@Param("orderId") String orderId,@Param("excludeAction") String excludeAction);
  /** 按 orderId, excludeAction 查询 rental_order_freight_account 记录。 */
  Offsets selectOrderDeductionOffsets(@Param("orderId") String orderId,@Param("excludeAction") String excludeAction);
  /** 按 actionId 查询 admin_case_action 记录。 */
  List<Refund> selectPlannedRefundsByAction(@Param("actionId") String actionId);
  /** 按 order 查询 order_claim 记录。 */
  boolean hasActiveClaimOrArbitration(@Param("order") String order);
  /** 按 orderId 查询 order_outbound_record 记录。 */
  boolean selectIssuePlanDispatched(@Param("orderId") String orderId);
  /** 按 orderId 查询 order_return_check 记录。 */
  boolean physicallyReceived(@Param("orderId") String orderId);
  /** 按 orderId 查询 order_extension_request 记录。 */
  boolean hasOpenExtension(@Param("orderId") String orderId);
  /** 按 caseId, orderId, type 查询 admin_case_relation 记录。 */
  boolean referenceExists(@Param("caseId") String caseId,@Param("orderId") String orderId,@Param("type") String type,@Param("id") String id);
  /** 按 id, version, type 更新 order_issue_report 记录。 */
  int resolveIssue(@Param("id") String id,@Param("version") int version,@Param("type") String type,
      @Param("summary") String summary,@Param("done") boolean done,@Param("at") LocalDateTime at);
  /** 按 actionId, at 更新 refund_order 记录。 */
  int cancelDraftRefunds(@Param("actionId") String actionId,@Param("at") LocalDateTime at);
  /** 按 id, replacement, at 更新 admin_case_action 记录。 */
  void cancelPlan(@Param("id") String id,@Param("replacement") String replacement,@Param("at") LocalDateTime at);
  /** 按 id, at 更新 order_deduction 记录。 */
  void cancelDeduction(@Param("id") String id,@Param("at") LocalDateTime at);
  /** 按 id, orderId, actionId 新增 order_deduction 记录。 */
  void reserveDeduction(@Param("id") String id,@Param("orderId") String orderId,@Param("actionId") String actionId,
      @Param("actor") String actor,@Param("amount") int amount,@Param("reason") String reason,@Param("at") LocalDateTime at);
  /** 按 actionId, at 更新 order_deduction 记录。 */
  int confirmDeduction(@Param("actionId") String actionId,@Param("at") LocalDateTime at);
  /** 按 orderId, at 更新 rental_order 记录。 */
  void updateTotals(@Param("orderId") String orderId,@Param("at") LocalDateTime at);
  /** 按 orderId, at 更新 rental_order_freight_account 记录。 */
  void settleTerminationFreight(@Param("orderId") String orderId,@Param("at") LocalDateTime at);
  /** 按 orderId, at 更新 order_return_check 记录。 */
  void stopNormalClosure(@Param("orderId") String orderId,@Param("at") LocalDateTime at);
  /** 按 orderId, at 更新 rental_order 记录。 */
  int terminateOrder(@Param("orderId") String orderId,@Param("at") LocalDateTime at);
  /** 按 action, actor, at 更新 admin_case_action 记录。 */
  void finishPlan(@Param("action") Action action,@Param("actor") String actor,@Param("at") LocalDateTime at);
  /** 按 id, at 更新 admin_case_action 记录。 */
  void finishSteps(@Param("id") String id,@Param("at") LocalDateTime at);
  /** 按 issueId, at 更新 order_issue_report 记录。 */
  void closeIssue(@Param("issueId") String issueId,@Param("at") LocalDateTime at);
  /** 按 caseId, actor, at 更新 admin_case 记录。 */
  void closeCase(@Param("caseId") String caseId,@Param("actor") String actor,@Param("at") LocalDateTime at);
  /** 按 refundId 查询 admin_case_action 记录。 */
  String selectRefundParent(@Param("refundId") String refundId);
  /** 按 refundId, at 更新 refund_order 记录。 */
  void markSubmitting(@Param("refundId") String refundId,@Param("at") LocalDateTime at);
  /** 按 actionId, at 更新 admin_case_action 记录。 */
  void lockTerminationPlanForRefundSubmission(@Param("actionId") String actionId,@Param("at") LocalDateTime at);
  /** 按 afterId 查询 admin_case_action 记录。 */
  List<String> pendingOrders(@Param("afterId") String afterId);
}
