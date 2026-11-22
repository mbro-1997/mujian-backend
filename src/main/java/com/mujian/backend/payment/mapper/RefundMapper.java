package com.mujian.backend.payment.mapper;

import com.mujian.backend.payment.domain.RefundOrderDO;
import com.mujian.backend.payment.dto.PaymentWrites;
import com.mujian.backend.payment.dto.RefundQuery;
import com.mujian.backend.payment.dto.RefundWrites;
import com.mujian.backend.payment.projection.RefundCouponReturnProjection;
import com.mujian.backend.payment.projection.RefundExposureProjection;
import com.mujian.backend.payment.projection.RefundSourceProjection;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RefundMapper {
  /** 按 ext, sub, orderId 查询 order_extension_request 记录。 */
  boolean extensionSuborderMatches(@Param("ext") String ext,@Param("sub") String sub,@Param("orderId") String orderId);
  /** 正常资金义务仅接受本订单已关闭核验或已核准申报，不以调用者给出的ID猜归属。 */
  boolean selectNormalRefundSourceExists(
      @Param("sourceType") String sourceType,
      @Param("sourceId") String sourceId,
      @Param("orderId") String orderId);

  /** 本批次恢复只需原退款标识，不读取完整资金快照。 */
  List<String> selectRefundIdsByBatch(@Param("batchId") String batchId);

  /** 单订单详情在数据库限定原订单范围，避免取同批其他工厂退款后丢弃。 */
  List<String> selectRefundIdsByOrder(@Param("orderId") String orderId);

  /** 已获权退款集合批量读取原支付返券，避免每行重复查询批次。 */
  List<RefundCouponReturnProjection> selectCouponReturnsByPayments(
      @Param("paymentIds") Collection<String> paymentIds);

  /** 按原业务来源、工厂和退款状态分页。 */
  List<RefundOrderDO> selectRefundPage(@Param("query") RefundQuery refundQuery);

  /** 沿退款列表相同条件统计原义务。 */
  long countRefunds(@Param("query") RefundQuery refundQuery);

  /** 原退款号租约；未知和失败都保留额度。 */
  int claimRefundAttempt(@Param("attempt") PaymentWrites.Attempt attempt);

  /** 仅租约持有者安排下一检查，不覆盖已确认成功。 */
  int finishRefundAttempt(@Param("finish") PaymentWrites.Finish finish);

  /** 已核验资金单向合并；成功不能被迟到处理中或失败回退。 */
  int mergeRefundFact(@Param("fact") RefundWrites.Fact fact);

  /** 重算已成功现金，重复通知不会重复累加。 */
  void refreshSuborderRefunds(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 从原子退款成功事实汇总原支付已退款额。 */
  void refreshPaymentRefunds(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 从成功退款更新订单各现金科目，不把在途视为已退。 */
  void refreshOrderRefunds(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 按成功退款更新原运费账户，不改变预收原值。 */
  void refreshFreightRefunds(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 全部原订单的合法扣款阻止整券返回。 */
  boolean hasBatchDeductions(@Param("id") String id);

  /** 有限恢复队列，成功和取消不重试。 */
  List<String> selectDueRefundIds(@Param("now") LocalDateTime now);

  /** 稳定来源键防重，不受客户端请求号变化影响。 */
  RefundOrderDO selectRefundBySourceKey(@Param("key") String key);

  /** 退款主记录用于执行和详情，可按原根锁序加锁。 */
  RefundOrderDO selectRefundById(@Param("id") String id, @Param("lock") boolean lock);

  /** 成功、未知、明确失败仍保留原义务额度，只有确证取消才释放。 */
  RefundExposureProjection selectRefundExposureBySuborder(@Param("id") String id);

  /** 在原支付/库存/券根锁之后创建唯一退款义务。 */
  void insertRefund(@Param("refund") RefundOrderDO refundOrderDO);

  /** 按批次读取全部原子单退款，不遗漏未使用优惠的工厂。 */
  List<RefundOrderDO> selectRefundsByPayment(@Param("id") String id);
  /** 按 id 查询 refund_order 记录。 */
  List<RefundOrderDO> selectRefundsByBatch(@Param("id") String id);

  /** 按原商户退款号找到不可变原支付及原商户快照。 */
  RefundSourceProjection selectRefundSourceByChannelNo(@Param("no") String no);
}
