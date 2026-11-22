package com.mujian.backend.payment.mapper;

import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.dto.PaymentQuery;
import com.mujian.backend.payment.dto.PaymentWrites;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PaymentOrderMapper {
  record Source(String scene,String extensionId,String receivableId,String customOrderId) {}
  /** 按 id 查询 payment_channel_suborder 记录。 */
  Source selectSource(@Param("id") String id);

  /** 执行退款义务前锁定其唯一渠道子单。 */
  ChannelSuborderDO lockSuborderById(@Param("id") String id);

  /** 按工厂EXISTS及状态分页，避免多工厂重复主支付。 */
  List<PaymentOrderDO> selectPaymentPage(@Param("query") PaymentQuery paymentQuery);

  /** 与主支付列表共用筛选，不按子单数量重复计数。 */
  long countPayments(@Param("query") PaymentQuery paymentQuery);

  /** 到期、未终局支付或待恢复成单的原批次，有限扫描。 */
  List<String> selectDuePaymentBatches(@Param("now") LocalDateTime now);

  /** 原号prepay可由迟到响应补齐，不改写支付资金状态或截止。 */
  void rememberPrepay(
      @Param("id") String id, @Param("prepay") String prepay, @Param("now") LocalDateTime now);

  /** CAS领取短期执行租约，网络调用发生于该事务提交后。 */
  int claimAttempt(@Param("attempt") PaymentWrites.Attempt attempt);

  /** 只有仍持有原版本租约的执行者能保存prepay或失败调度，不覆盖新事实。 */
  int finishAttempt(@Param("finish") PaymentWrites.Finish finish);

  /** 子单成功单向确认，关闭后晚付仍保留真实实收。 */
  int confirmSuborderPaid(@Param("fact") PaymentWrites.SuborderFact suborderFact);

  /** 未确认收款的子单才可记关闭，迟到未付不会回退成功。 */
  int confirmSuborderClosed(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 依据已持久子单汇总主支付，不以渠道合计覆盖原明细。 */
  void updatePaymentSummary(@Param("summary") PaymentWrites.Summary summary);

  /** 关单意图先落库，再由统一执行器调用外部。 */
  void requestClose(
      @Param("id") String id, @Param("reason") String reason, @Param("now") LocalDateTime now);

  /** 初次提交只创建一张主支付单。 */
  void insertPayment(@Param("payment") PaymentOrderDO paymentOrderDO);

  /** 每厂一张原子单，原号贯穿支付、退款与恢复。 */
  void insertSuborder(@Param("suborder") ChannelSuborderDO channelSuborderDO);

  /** 按批次读取唯一主支付并可加锁。 */
  PaymentOrderDO selectPaymentByBatch(@Param("id") String id, @Param("lock") boolean lock);

  /** 按原支付编号读取根事实，恢复时可以按既定锁序锁定。 */
  PaymentOrderDO selectPaymentById(@Param("id") String id, @Param("lock") boolean lock);

  /** 按原支付读取子单，顺序固定。 */
  List<ChannelSuborderDO> selectSubordersByPayment(
      @Param("id") String id, @Param("lock") boolean lock);
}
