package com.mujian.backend.custom.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.custom.dto.CustomForms.*;
import java.time.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CustomFulfillmentMapper {

  record Delivery(
      String id,
      int deliveryVersion,
      String mode,
      String state,
      LocalDateTime pickupRegisteredAt,
      LocalDateTime deliveredAt,
      String evidence,
      String remark) {}

  record Extension(
      String id,
      String orderId,
      int version,
      String state,
      LocalDate originalDate,
      LocalDate requestedDate,
      String reason,
      String evidence,
      String decisionNote,
      String caseId) {}

  record Sale(
      String id,
      String orderId,
      int version,
      String state,
      String deliveryId,
      String problem,
      String evidence,
      String requestedSolution,
      String resolutionType,
      String resolutionNote,
      int goodsRefundFen,
      int freightRefundFen,
      String refundId,
      String refundStatus,
      String replacementDeliveryId,
      String issueType,
      String caseId) {}

  record Due(String id, String delivery, int deliveryVersion) {}

  /** 按 order 查询 custom_delivery_record 记录。 */
  List<Delivery> selectDeliveriesByCustomOrder(@Param("order") String order);

  /** 按 id 查询 custom_delivery_record 记录。 */
  Delivery selectCustomDeliveryRecordById(@Param("id") String id);

  /** 按 id, order, key 新增 custom_delivery_record 记录。 */
  int insertCustomDeliveryRecord(
      @Param("id") String id,
      @Param("order") String order,
      @Param("key") String key,
      @Param("actor") String actor,
      @Param("pickup") boolean pickup,
      @Param("evidence") String evidence,
      @Param("items") String items,
      @Param("remark") String remark,
      @Param("occurred") LocalDateTime occurred,
      @Param("at") LocalDateTime at);

  /** 按 order, delivery, at 更新 custom_order 记录。 */
  int activateDelivery(
      @Param("order") String order,
      @Param("delivery") String delivery,
      @Param("at") LocalDateTime at);

  /** 按 id, at 更新 custom_delivery_record 记录。 */
  int updateCustomDeliveryRecordPickup(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 按 id, delivery, at 更新 custom_order 记录。 */
  int completeCustomOrder(
      @Param("id") String id, @Param("delivery") String delivery, @Param("at") LocalDateTime at);

  /** 按 at 查询 custom_order 记录。 */
  List<Due> selectOrdersDueForAcceptance(@Param("at") LocalDateTime at);

  /** 按 order, factory 查询 custom_order_extension_request 记录。 */
  List<Extension> selectExtensionsByCustomOrder(@Param("order") String order, @Param("factory") boolean factory);

  /** 按 id 查询 custom_order_extension_request 记录。 */
  Extension selectCustomOrderExtensionRequestById(@Param("id") String id);

  /** 按 order 查询 custom_order_extension_request 记录。 */
  String activeExtension(@Param("order") String order);

  /** 按 order 查询 custom_order_extension_request 记录。 */
  String draftExtension(@Param("order") String order);

  /** 按 id, order, key 新增 custom_order_extension_request 记录。 */
  int insertCustomOrderExtensionRequestDraft(
      @Param("id") String id,
      @Param("order") String order,
      @Param("key") String key,
      @Param("input") ExtensionInput input,
      @Param("evidence") String evidence,
      @Param("at") LocalDateTime at);

  /** 按 id, caseId, original 更新 custom_order_extension_request 记录。 */
  int submitExtension(
      @Param("id") String id,
      @Param("caseId") String caseId,
      @Param("original") LocalDate original,
      @Param("at") LocalDateTime at);

  /** 按 id, decision, at 更新 custom_order_extension_request 记录。 */
  int updateCustomOrderExtensionRequestDecide(
      @Param("id") String id,
      @Param("decision") ExtensionDecision decision,
      @Param("at") LocalDateTime at);

  /** 按 order, date, at 更新 custom_order 记录。 */
  int updatePromisedCompletionDate(
      @Param("order") String order, @Param("date") LocalDate date, @Param("at") LocalDateTime at);

  /** 按 order 查询 custom_after_sale_request 记录。 */
  List<Sale> selectAfterSalesByCustomOrder(@Param("order") String order);

  /** 按 id 查询 custom_after_sale_request 记录。 */
  Sale selectCustomAfterSaleRequestById(@Param("id") String id);

  /** 按 order 查询 custom_after_sale_request 记录。 */
  Sale activeSale(@Param("order") String order);

  /** 按 id, order, actor 新增 custom_after_sale_request 记录。 */
  int insertCustomAfterSaleRequest(
      @Param("id") String id,
      @Param("order") String order,
      @Param("actor") String actor,
      @Param("key") String key,
      @Param("input") Issue input,
      @Param("evidence") String evidence,
      @Param("caseId") String caseId,
      @Param("at") LocalDateTime at);

  /** 按 order, at 更新 custom_order 记录。 */
  int pauseCustomOrderAcceptance(@Param("order") String order, @Param("at") LocalDateTime at);

  /** 按 id, at 更新 custom_after_sale_request 记录。 */
  int processSale(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 按 id, input, state 更新 custom_after_sale_request 记录。 */
  int updateCustomAfterSaleRequestResolve(
      @Param("id") String id,
      @Param("input") Resolution input,
      @Param("state") String state,
      @Param("refund") String refund,
      @Param("at") LocalDateTime at);

  /** 按 id, delivery, at 更新 custom_after_sale_request 记录。 */
  int updateCustomAfterSaleRequestReplacement(
      @Param("id") String id, @Param("delivery") String delivery, @Param("at") LocalDateTime at);

  /** 按 id, at 更新 custom_delivery_record 记录。 */
  int markCustomDeliveryException(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 按 id, at 更新 custom_after_sale_request 记录。 */
  int markAfterSaleResolved(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 按 id, replacement, at 更新 custom_order 记录。 */
  int resumeCustomOrderAcceptance(
      @Param("id") String id,
      @Param("replacement") boolean replacement,
      @Param("at") LocalDateTime at);

  /** 按 id, at 更新 custom_order 记录。 */
  int waitForRefund(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 按 id, at 更新 custom_order 记录。 */
  int markCustomOrderCancelled(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 按 id, actor, note 更新 admin_case 记录。 */
  int closeCase(
      @Param("id") String id,
      @Param("actor") String actor,
      @Param("note") String note,
      @Param("at") LocalDateTime at);

  /** 按 status, page 查询 custom_order_extension_request 记录。 */
  List<Extension> extensionsPage(@Param("status") String status, @Param("page") PageQuery page);

  /** 按 status 查询 custom_order_extension_request 记录。 */
  long extensionsCount(@Param("status") String status);

  /** 按 status, page 查询 custom_after_sale_request 记录。 */
  List<Sale> salesPage(@Param("status") String status, @Param("page") PageQuery page);

  /** 按 status 查询 custom_after_sale_request 记录。 */
  long salesCount(@Param("status") String status);
}
