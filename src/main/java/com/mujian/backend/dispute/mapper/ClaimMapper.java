package com.mujian.backend.dispute.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.dispute.dto.ClaimInput;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ClaimMapper {
  record Row(String id,String orderId,String orderNo,String factoryId,String buyerId,String factoryName,int version,LocalDateTime createdAt,
      String returnCheckId,String caseId,String status,String problemType,String summary,Integer claimedFen,Integer approvedLossFen,Integer availableDepositFen,
      LocalDateTime responseDeadlineAt,String reviewRemark,String appealReason,String arbitrationId,String items,String evidence) {}
  record Filter(String id,String orderId,String buyerId,String factoryId,boolean admin,String status) {}
  /** 按筛选条件分页读取索赔记录。 */
  List<Row> selectOrderClaimPageByQuery(@Param("filter") Filter filter,@Param("page") PageQuery page);
  /** 按 filter 统计 order_claim 记录。 */
  long selectOrderClaimCountByQuery(@Param("filter") Filter filter);
  /** 按指定条件读取索赔记录。 */
  Row selectClaimById(@Param("id") String id);
  /** 按 id, order, check 新增 order_claim 记录。 */
  void insertOrderClaimDraft(@Param("id") String id,@Param("order") String order,@Param("check") String check,@Param("actor") String actor,@Param("key") String key,@Param("at") LocalDateTime at);
  /** 按 id, input, items 更新 order_claim 记录。 */
  int updateOrderClaim(@Param("id") String id,@Param("input") ClaimInput input,@Param("items") String items,@Param("evidence") String evidence,@Param("at") LocalDateTime at,@Param("advance") boolean advance);
  /** 按 check, at 更新 order_return_check 记录。 */
  int updateOrderReturnCheckFreeze(@Param("check") String check,@Param("at") LocalDateTime at);
  /** 按 check, at 更新 order_return_check 记录。 */
  void releaseClaim(@Param("check") String check,@Param("at") LocalDateTime at);
  /** 按 order, at 更新 inventory_reservation 记录。 */
  void freezeInventory(@Param("order") String order,@Param("at") LocalDateTime at);
  /** 按 order, at 更新 inventory_reservation 记录。 */
  void thawInventory(@Param("order") String order,@Param("at") LocalDateTime at);
  /** 按 order, at 更新 rental_order 记录。 */
  void freezeDeposit(@Param("order") String order,@Param("at") LocalDateTime at);
  /** 按 order, at 更新 rental_order 记录。 */
  void thawDeposit(@Param("order") String order,@Param("at") LocalDateTime at);
  /** 按 order, at 更新 order_return_check 记录。 */
  boolean updateOrderReturnCheckResume(@Param("order") String order,@Param("at") LocalDateTime at);
  /** 按 id, state, review 更新 order_claim 记录。 */
  int updateOrderClaimState(@Param("id") String id,@Param("state") String state,@Param("review") String review,@Param("reason") String reason,@Param("loss") Integer loss,
      @Param("deposit") Integer deposit,@Param("actor") String actor,@Param("at") LocalDateTime at);
  /** 按 id, refs, at 更新 order_claim 记录。 */
  void updateOrderClaimEvidence(@Param("id") String id,@Param("refs") String refs,@Param("at") LocalDateTime at);
  /** 按 id, order, actor 查询 order_evidence_record 记录。 */
  boolean usableEvidence(@Param("id") String id,@Param("order") String order,@Param("actor") String actor);
  /** 按 id, order, ids 更新 order_evidence_record 记录。 */
  void bindUploads(@Param("id") String id,@Param("order") String order,@Param("ids") List<String> ids);
  /** 按 id, status, actor 更新 admin_case 记录。 */
  void caseState(@Param("id") String id,@Param("status") String status,@Param("actor") String actor,@Param("at") LocalDateTime at);
  /** 按 id, claim, type 新增 claim_user_response 记录。 */
  void insertClaimUserResponse(@Param("id") String id,@Param("claim") Row claim,@Param("type") String type,@Param("reason") String reason,@Param("at") LocalDateTime at);
  /** 按 id, claim, actor 新增 arbitration_case 记录。 */
  void insertArbitrationCaseAppeal(@Param("id") String id,@Param("claim") Row claim,@Param("actor") String actor,@Param("summary") String summary,@Param("at") LocalDateTime at);
  /** 按 id, arb, evidence 新增 arbitration_evidence_link 记录。 */
  void arbitrationEvidence(@Param("id") String id,@Param("arb") String arb,@Param("evidence") String evidence,@Param("role") String role,@Param("at") LocalDateTime at);
  /** 按 at, after 查询 order_claim 记录。 */
  List<String> selectClaimIdsPastResponseDeadline(@Param("at") LocalDateTime at,@Param("after") String after);
  /** 按 after 查询 order_return_check 记录。 */
  List<String> selectFrozenReturnCheckOrderIds(@Param("after") String after);
}
