package com.mujian.backend.order.mapper;

import java.time.*;
import java.util.List;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.order.service.OrderExtensionQuote.Quote;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ExtensionRequestMapper {
  record Row(String id,String orderId,String orderNo,String factoryId,String buyerId,int version,LocalDateTime createdAt,
      String status,int extraDays,LocalDate originalDueDate,LocalDate requestedDueDate,int rentFen,String paymentId,
      LocalDateTime paidAt,LocalDateTime warningAt,LocalDateTime deadlineAt,String reason,String refundId,String refundStatus,
      Integer refundFen,LocalDateTime latestPaidAt,String remark,LocalDateTime paymentExpireAt) {}
  record Filter(String id,String orderId,String buyerId,String factoryId,String status) {}
  /** 按筛选条件分页读取延期申请记录。 */
  List<Row> selectExtensionPageByQuery(@Param("filter") Filter filter,@Param("page") PageQuery page);
  /** 按 filter 统计 order_extension_request 记录。 */
  long selectOrderExtensionRequestCountByQuery(@Param("filter") Filter filter);
  /** 按指定条件读取延期申请记录。 */
  Row selectExtensionById(@Param("id") String id);
  /** 按 orderId 查询 order_extension_request 记录。 */
  String selectActiveExtensionIdByOrder(@Param("orderId") String orderId);
  /** 按 paymentId 查询 order_extension_request 记录。 */
  String selectOrderExtensionRequestByPaymentId(@Param("paymentId") String paymentId);
  /** 按 paymentId 查询 payment_order 记录。 */
  String fundingBatch(@Param("paymentId") String paymentId);
  /** 按 id, quote, remark 新增 order_extension_request 记录。 */
  int insertOrderExtensionRequest(@Param("id") String id,@Param("quote") Quote quote,@Param("remark") String remark,@Param("at") LocalDateTime at);
  /** 按指定条件读取延期申请记录。 */
  int insertPaymentOrder(@Param("id") String id,@Param("payment") String payment,@Param("key") String key,@Param("expiry") LocalDateTime expiry,@Param("at") LocalDateTime at);
  /** 按 id, payment, sub 新增 payment_channel_suborder 记录。 */
  int insertPaymentChannelSuborder(@Param("id") String id,@Param("payment") String payment,@Param("sub") String sub,@Param("at") LocalDateTime at);
  /** 按 id, payment 更新 order_extension_request 记录。 */
  void bindExtensionPayment(@Param("id") String id,@Param("payment") String payment);
  /** 按 id, ext, source 新增 inventory_reservation 记录。 */
  int insertInventoryReservationHold(@Param("id") String id,@Param("ext") String ext,@Param("source") String source,@Param("expiry") LocalDateTime expiry,@Param("at") LocalDateTime at);
  /** 按 id, from, to 更新 inventory_reservation 记录。 */
  int transitionHolds(@Param("id") String id,@Param("from") String from,@Param("to") String to,@Param("at") LocalDateTime at);
  /** 按 id, ext, at 新增 payment_allocation 记录。 */
  int insertPaymentAllocation(@Param("id") String id,@Param("ext") String ext,@Param("at") LocalDateTime at);
  /** 按 orderId, fen, at 更新 rental_order 记录。 */
  void recordCash(@Param("orderId") String order,@Param("fen") int fen,@Param("at") LocalDateTime at);
  /** 按 id, paidAt, at 更新 order_extension_request 记录。 */
  int updateOrderExtensionRequestPaid(@Param("id") String id,@Param("paidAt") LocalDateTime paidAt,@Param("at") LocalDateTime at);
  /** 按 id, status, reason 更新 order_extension_request 记录。 */
  int updateExtensionStatusAndRefund(@Param("id") String id,@Param("status") String status,@Param("reason") String reason,@Param("refund") String refund,@Param("at") LocalDateTime at);
  /** 按 ext, at 更新 inventory_reservation 记录。 */
  int mergeDates(@Param("ext") Row ext,@Param("at") LocalDateTime at);
  /** 按 ext, at 更新 rental_order 记录。 */
  int effectiveDate(@Param("ext") Row ext,@Param("at") LocalDateTime at);
  /** 按 at, after 查询 order_extension_request 记录。 */
  List<String> selectOrderExtensionRequestDueForProcessing(@Param("at") LocalDateTime at,@Param("after") String after);
  /** 按 refund, at 更新 order_extension_request 记录。 */
  void markRefunded(@Param("refund") String refund,@Param("at") LocalDateTime at);
}
