package com.mujian.backend.settlement.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 正常结算只引用已形成的订单、原渠道子单、分摊与按科目成功退款。 */
@Mapper
public interface SettlementMapper {
  record Candidate(
      String id,
      String buyer,
      String owner,
      String factory,
      String status,
      int rent,
      int rentReturned,
      int deposit,
      int depositReturned,
      int freight,
      int freightReturned,
      int approved,
      int reimbursement,
      int cancellation,int depositDeducted,boolean incremental,boolean terminated) {}

  record Source(
      String allocationId,
      String paymentOrderId,
      String suborderId,
      String scene,
      String accountId,
      String merchant,
      String transactionId,
      String accountName,
      String masked,
      String state,
      int paid,
      int refunded,
      int cashRentFen,
      int cashDepositFen,
      int cashFreightFen,
      int coupon,
      int compensation,
      int other,
      int total) {}

  record Refund(
      String id, String suborderId, int rent, int deposit, int freight, int other, int amount) {}

  record Basis(String id, String kind, String component, String sourceId, int amount) {}

  record Detail(
      String id,
      String orderId,
      String paymentId,
      String snapshot,
      int rent,
      int coupon,
      int rentReturned,
      int cancellation,
      int base,
      int commission,
      int approved,
      int freightReturned,
      int reimbursement,
      int unlock,String key,int extension,int depositCompensation,int supplement,int overdue,int overdueCommission,int rentOffset) {}

  record Batch(
      String id,
      String factory,
      LocalDateTime start,
      LocalDateTime end,
      LocalDateTime at,
      int count,
      long base,
      long commission,
      long freight,
      long payable,long depositCompensation,long goodsBase) {}

  record CustomCandidate(String id,String buyer,String owner,String factory,int rate,int goods,int freight,int refunded){}
  record Returns(int goods,int freight){}
  record CustomDetail(CustomCandidate order,Returns refund,int base,int netFreight,Chain chain){}
  /** 按 factory, cutoff 查询 custom_order 记录。 */
  List<CustomCandidate> customCandidates(@Param("factory") String factory,@Param("cutoff") LocalDateTime cutoff);
  /** 按 id 查询并按调用条件锁定 custom_order 记录。 */
  String lockCustomOrder(@Param("id") String id);
  /** 按 id 查询并按调用条件锁定 custom_order 记录。 */
  Source customSource(@Param("id") String id);
  /** 按 sub 查询 refund_order 记录。 */
  Returns customRefunds(@Param("sub") String sub);
  /** 按 batch, customDetail 新增 custom_order_settlement 记录。 */
  void insertCustomDetail(@Param("batch") Batch batch,@Param("customDetail") CustomDetail customDetail);
  /** 按 id, batch, customDetail 新增 platform_commission_record 记录。 */
  void insertCustomCommission(@Param("id") String id,@Param("batch") Batch batch,@Param("customDetail") CustomDetail customDetail);

  record Chain(
      String id,
      String commissionNo,
      String finishNo,
      String detailId,
      String orderId,
      Source source,
      int commission,
      int unlock) {}

  /** 按截止时刻分页选择仍有合格来源的工厂。 */
  List<String> selectRentalOrderFactories(@Param("cutoff") LocalDateTime cutoff, @Param("after") String after);

  /** 读取工厂本期尚未入批的原订单与结清账户。 */
  List<Candidate> selectRentalOrderCandidates(
      @Param("factory") String factory, @Param("cutoff") LocalDateTime cutoff);

  /** 按工厂串行生成其当期结算批次。 */
  String lockFactory(@Param("factory") String factory);

  /** 按原租赁订单加锁，串行处理互斥动作。 */
  String lockOrder(@Param("id") String id);

  /** 检查该工厂本期批次是否已经固化。 */
  boolean selectSettlementFrozen(@Param("factory") String factory, @Param("end") LocalDateTime end);

  /** 锁定并返回每个原实收子单及不可变分摊。 */
  List<Source> selectSettlementSourcesByOrder(@Param("id") String id);

  /** 按原子单取得已经成功的退款科目。 */
  List<Refund> selectRefundsForSettlement(@Param("id") String id);

  /** 查询 factory_settlement_detail 记录。 */
  int frozenOverdue(@Param("id") String id);

  /** 读取已生效的优惠调整和取消扣款凭据。 */
  List<Basis> selectOrderPriceAdjustmentBasis(@Param("id") String id);

  /** 以工厂及周期唯一写入本期汇总。 */
  void insertBatch(@Param("batch") Batch batch);

  /** 保存原订单结算依据和逐来源快照。 */
  void insertDetail(@Param("batch") Batch batch, @Param("detail") Detail detail);

  /** 按订单取整后的金额记录平台佣金。 */
  void insertCommission(@Param("id") String id, @Param("batch") Batch batch, @Param("detail") Detail detail);

  /** 以原渠道子单状态条件保留该来源的分账权。 */
  int updatePaymentChannelSuborderReserve(@Param("chain") Chain chain, @Param("batch") Batch batch);

  /** 为该来源固化分佣及解冻各自的原单号。 */
  void insertChain(@Param("batch") Batch batch, @Param("chain") Chain chain);

  /** 记录本批次关联的结算支付尝试。 */
  void insertAttempt(@Param("id") String id, @Param("batch") Batch batch, @Param("source") Source source);
}
