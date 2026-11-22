package com.mujian.backend.custom.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.custom.dto.CustomForms.*;
import com.mujian.backend.payment.dto.PaymentAccount;
import java.time.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CustomOrderMapper {

  record Order(
      String id,
      String number,
      int version,
      String requirementId,
      String quoteId,
      String factoryId,
      String buyerId,
      String state,
      String paymentId,
      String deliveryMode,
      String address,
      LocalDate promisedDate,
      int goods,
      int freight,
      int totalFen,
      int paid,
      int refundedFen,
      LocalDateTime startedAt,
      String deliveryId,
      String acceptance,
      LocalDateTime deadline,
      Integer remaining,
      LocalDateTime updatedAt,
      LocalDateTime quoteDeadline) {
    /** 检查当前交付记录是否仍处于可申请售后的验收窗口。 */
    public boolean acceptsAfterSale(String delivery, LocalDateTime at) {
      return "pending_acceptance".equals(state)
          && "running".equals(acceptance)
          && deliveryId != null
          && deliveryId.equals(delivery)
          && deadline != null
          && at.isBefore(deadline);
    }
  }

  record OrderItem(
      String id,
      String itemId,
      String title,
      int quantity,
      int unitPriceFen,
      String dimension,
      String material,
      String style,
      String acceptanceStandard) {}

  record Exposure(int goods, int freight, int pending) {}

  /** 按 id, lock 查询并按调用条件锁定 custom_order 记录。 */
  Order selectCustomOrderById(@Param("id") String id, @Param("lock") boolean lock);

  record OrderSummary(String id, String number, int version, String requirementId, String factoryId,
                      String state, int totalFen, LocalDateTime updatedAt) {}

  /** 按 buyer, factory, status 分页查询 custom_order 记录。 */
  List<OrderSummary> selectCustomOrderPageByQuery(
      @Param("buyer") String buyer,
      @Param("factory") String factory,
      @Param("status") String status,
      @Param("page") PageQuery page);

  /** 按 buyer, factory, status 统计 custom_order 记录。 */
  long selectCustomOrderCountByQuery(
      @Param("buyer") String buyer,
      @Param("factory") String factory,
      @Param("status") String status);

  /** 按 id 查询 custom_order_item 记录。 */
  List<OrderItem> selectCustomOrderItemsByOrderId(@Param("id") String id);

  /** 按 id, quote, mode 新增 custom_order 记录。 */
  int insertCustomOrder(
      @Param("id") String id,
      @Param("quote") String quote,
      @Param("mode") String mode,
      @Param("address") String address,
      @Param("at") LocalDateTime at);

  /** 按 id, root, quote 新增 custom_order_item 记录。 */
  int freezeItem(
      @Param("id") String id,
      @Param("root") String root,
      @Param("quote") String quote,
      @Param("item") QuotedItem item,
      @Param("terms") String terms,
      @Param("source") String source,
      @Param("sort") int sort,
      @Param("at") LocalDateTime at);

  /** 按 id, buyer, key 新增 payment_order 记录。 */
  int insertPaymentOrder(
      @Param("id") String id,
      @Param("buyer") String buyer,
      @Param("key") String key,
      @Param("account") PaymentAccount account,
      @Param("openid") String openid,
      @Param("total") int total,
      @Param("at") LocalDateTime at);

  /** 按 id, order, payment 新增 payment_channel_suborder 记录。 */
  int insertPaymentChannelSuborder(
      @Param("id") String id,
      @Param("order") String order,
      @Param("payment") String payment,
      @Param("total") int total,
      @Param("at") LocalDateTime at);

  /** 按 id, payment, at 更新 custom_order 记录。 */
  int bindCustomOrderPayment(@Param("id") String id, @Param("payment") String payment, @Param("at") LocalDateTime at);

  /** 按 payment 查询 payment_channel_suborder 记录。 */
  String fundingOrder(@Param("payment") String payment);

  /** 按 id, order, payment 新增 payment_allocation 记录。 */
  int insertPaymentAllocation(
      @Param("id") String id,
      @Param("order") String order,
      @Param("payment") String payment,
      @Param("sub") String sub,
      @Param("at") LocalDateTime at);

  /** 按 id, payment, goods 更新 custom_order 记录。 */
  int updateCustomOrderPaid(
      @Param("id") String id,
      @Param("payment") String payment,
      @Param("goods") int goods,
      @Param("freight") int freight,
      @Param("at") LocalDateTime at);

  /** 按 id, state, at 更新 custom_order 记录。 */
  int updateCustomOrderState(@Param("id") String id, @Param("state") String state, @Param("at") LocalDateTime at);

  record OrderExposure(String orderId, int goods, int freight, int pending) {}

  /** 按 ids 分页查询 payment_allocation 记录。 */
  List<OrderExposure> pageExposures(@Param("ids") List<String> ids);

  /** 按 sub 查询 payment_allocation 记录。 */
  Exposure selectRefundableCustomAmountsBySuborder(@Param("sub") String sub);

  /** 按 id, key, order 新增 refund_order 记录。 */
  int insertRefundOrder(
      @Param("id") String id,
      @Param("key") String key,
      @Param("order") String order,
      @Param("sub") String sub,
      @Param("actor") String actor,
      @Param("type") String type,
      @Param("reason") String reason,
      @Param("goods") int goods,
      @Param("freight") int freight,
      @Param("at") LocalDateTime at);

  /** 按 id, at 更新 custom_order 记录。 */
  int refreshRefunded(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 按 at 查询 custom_order 记录。 */
  List<String> selectCustomOrderExpired(@Param("at") LocalDateTime at);
}
