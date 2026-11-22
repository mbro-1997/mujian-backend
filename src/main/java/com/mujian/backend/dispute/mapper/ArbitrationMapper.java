package com.mujian.backend.dispute.mapper;
import java.time.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import org.apache.ibatis.annotations.*;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.dispute.dto.ArbitrationInput;
@Mapper
public interface ArbitrationMapper {
  record Row(String id,String orderId,String orderNo,String factoryId,String buyerId,String factoryName,int version,LocalDateTime createdAt,String caseNo,String status,
      String claimId,String issueId,String appealReason,String caseId,String request,String party){}
  record Decision(String id,String status,String responsibility,String summary,int loss,int deposit,int excess,int rent,String refunds,String stock,LocalDateTime confirmedAt,String receivable,String refundBasis,String caseId,String failureReason){}
  record Reservation(String id,String itemId,String skuId,String snId,int quantity,String status,String name,String snCode,@JsonIgnore String orderId){}
  record Ref(String relationType,String relationId){}
  record SourceBalance(int paidFen,int deductedFen,int refundedFen,int reservedFen,@JsonIgnore String orderId){}
  /** 按指定条件读取仲裁记录。 */
  Row selectArbitrationById(@Param("id") String id);
  /** 按筛选条件分页读取仲裁记录。 */
  List<Row> selectArbitrationPageByQuery(@Param("claimFilter") ClaimMapper.Filter claimFilter,@Param("pageQuery") PageQuery pageQuery);
  /** 按 claimFilter 统计 arbitration_case 记录。 */
  long selectArbitrationCaseCountByQuery(@Param("claimFilter") ClaimMapper.Filter claimFilter);
  record EvidenceLink(String caseId, String evidenceId) {}
  record ExecutionLink(String decisionId, String relationType, String relationId, String refundStatus, Integer refundFen) {}
  /** 按 ids, role 查询 arbitration_evidence_link 记录。 */
  List<EvidenceLink> evidenceForCases(@Param("ids") List<String> ids, @Param("role") String role);
  /** 按 ids 查询 admin_case_action 记录。 */
  List<Decision> selectArbitrationDecisions(@Param("ids") List<String> ids);
  /** 按 ids 查询 inventory_reservation 记录。 */
  List<Reservation> stockForOrders(@Param("ids") List<String> ids);
  /** 按 ids 查询 order_deduction 记录。 */
  List<SourceBalance> selectArbitrationBalances(@Param("ids") List<String> ids);
  /** 按 ids 查询 admin_case_action 记录。 */
  List<ExecutionLink> executionLinks(@Param("ids") List<String> ids);
  /** 按 id, evidenceId 查询 arbitration_evidence_link 记录。 */
  boolean selectArbitrationLinked(@Param("id") String id,@Param("evidenceId") String evidenceId);
  /** 按 arbitration, id 查询 arbitration_case 记录。 */
  boolean caseEvidence(@Param("arbitration") String arbitration,@Param("id") String id);
  /** 按 order, type, source 查询 arbitration_case 记录。 */
  boolean validSource(@Param("order") String order,@Param("type") String type,@Param("source") String source);
  /** 按 id, order, actor 新增 arbitration_case 记录。 */
  void insertArbitrationCase(@Param("id") String id,@Param("order") String order,@Param("actor") String actor,@Param("input") ArbitrationInput.Open input,@Param("at") LocalDateTime at);
  /** 按 order, at 更新 order_receipt_check 记录。 */
  void freezeReceipt(@Param("order") String order,@Param("at") LocalDateTime at);
  /** 按 issue, actor, at 更新 admin_case 记录。 */
  void closeSourceCase(@Param("issue") String issue,@Param("actor") String actor,@Param("at") LocalDateTime at);
  /** 按 order, id, at 更新 order_issue_report 记录。 */
  void updateOrderIssueReportTransfer(@Param("order") String order,@Param("id") String issue,@Param("at") LocalDateTime at);
  /** 按 id, status, at 更新 arbitration_case 记录。 */
  void updateArbitrationCaseStage(@Param("id") String id,@Param("status") String status,@Param("at") LocalDateTime at);
  /** 按 order 查询 order_deduction 记录。 */
  SourceBalance selectArbitrationBalance(@Param("order") String order);
  /** 按 order 查询 inventory_reservation 记录。 */
  List<Reservation> selectArbitrationReservations(@Param("order") String order);
  /** 按 id 查询 admin_case_action 记录。 */
  Decision selectArbitrationDecision(@Param("id") String arb);
  /** 按 id, at 更新 arbitration_decision 记录。 */
  void retireDraft(@Param("id") String arb,@Param("at") LocalDateTime at);
  /** 按 id, row, arbitrationInput 新增 arbitration_decision 记录。 */
  void insertArbitrationDecisionDraft(@Param("id") String id,@Param("row") Row row,@Param("arbitrationInput") ArbitrationInput arbitrationInput,@Param("deposit") int deposit,@Param("stock") String stock,@Param("cash") String cash,@Param("actor") String actor,@Param("at") LocalDateTime at);
  /** 按 id, key, actor 更新 arbitration_decision 记录。 */
  int confirmArbitrationDecision(@Param("id") String decision,@Param("key") String key,@Param("actor") String actor,@Param("at") LocalDateTime at);
  /** 按 id, receivable 更新 arbitration_decision 记录。 */
  void linkReceivable(@Param("id") String decision,@Param("receivable") String receivable);
  /** 按 id 查询 order_deduction 记录。 */
  List<String> selectDeductionIdsByDecision(@Param("id") String decision);
  /** 按 id 查询 admin_case_action 记录。 */
  List<Ref> selectAdminCaseActionRefs(@Param("id") String decision);
  record Step(String id,String kind,String target,String payload,String status,String actor) {}
  /** 按 parent 查询 admin_case_action 记录。 */
  List<Step> selectAdminCaseActionSteps(@Param("parent") String parent);
  /** 按 id, order, at 更新 platform_intervention_record 记录。 */
  void releaseIntervention(@Param("id") String id,@Param("order") String order,@Param("at") LocalDateTime at);
  /** 按 after 查询 arbitration_case 记录。 */
  List<String> selectArbitrationCasePending(@Param("after") String after);
  /** 按 id 查询 admin_case_action 记录。 */
  String selectAdminCaseActionParent(@Param("id") String decision);
  /** 按 id, status, reason 更新 admin_case_action 记录。 */
  void actionState(@Param("id") String id,@Param("status") String status,@Param("reason") String reason,@Param("at") LocalDateTime at);
  /** 按 issue, at 更新 order_issue_report 记录。 */
  void resolveIssue(@Param("issue") String issue,@Param("at") LocalDateTime at);
}
