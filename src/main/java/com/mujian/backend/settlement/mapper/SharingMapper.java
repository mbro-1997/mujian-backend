package com.mujian.backend.settlement.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SharingMapper {
  record State(
      String id,
      String batchId,
      String orderId,
      String suborderId,
      String platform,
      String merchant,
      String transactionId,
      String shareNo,
      String finishNo,
      int fee,
      int unlockFen,
      String commission,
      String unfreeze,
      int commissionRetries,
      int unfreezeRetries,
      String status,
      String exceptionCode,
      boolean autoUnfrozen,
      LocalDateTime due,
      LocalDateTime lease,
      int version,
      boolean retryHasChannelOrder,boolean custom) {}

  /** 按 id 查询并按调用条件锁定 custom_order 记录。 */
  String lockCustom(@Param("id") String id);
  /** 按 id 查询 custom_order 记录。 */
  boolean customEligible(@Param("id") String id);
  /** 按 id, at 更新 custom_order_settlement 记录。 */
  int reconcileCustom(@Param("id") String id,@Param("at") LocalDateTime at);
  record Write(
      String commission,
      String unfreeze,
      String status,
      int commissionRetries,
      int unfreezeRetries,
      Integer actualFee,
      Integer actualUnlock,
      String channelShare,
      String channelFinish,
      String exception,
      String snapshot,
      boolean query,
      LocalDateTime due,
      LocalDateTime at) {}

  /** 分页读取到期且租约已释放的分账执行单。 */
  List<String> selectWechatProfitSharingOrderDueForProcessing(@Param("at") LocalDateTime at, @Param("after") String after);

  /** 从执行单及原支付来源读取当前状态和渠道身份。 */
  State selectWechatProfitSharingOrderState(@Param("id") String id);

  /** 按原租赁订单加锁，串行处理互斥动作。 */
  String lockOrder(@Param("id") String id);

  /** 锁定原渠道子单以排斥并发退款或分账。 */
  String lockSource(@Param("id") String id);

  /** 锁定批次以串行确认执行进度与已阅快照。 */
  String lockBatch(@Param("id") String id);

  /** 按版本领取当前阶段并保存租约有效期。 */
  int updateWechatProfitSharingOrderLease(
      @Param("row") State row, @Param("finish") boolean finish, @Param("at") LocalDateTime at);

  /** 仅在同一版本持久化渠道观察结果。 */
  int updateWechatProfitSharingOrderApplication(@Param("id") String id, @Param("version") int version, @Param("write") Write write);

  /** 已核实实际解冻后才将原来源转为已结。 */
  int settledSource(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 根据全部执行单实际结果更新结算支付尝试。 */
  void reconcilePayment(@Param("id") String batch, @Param("at") LocalDateTime at);

  /** 本明细全部来源成功后确认到账。 */
  void reconcileDetails(@Param("id") String batch, @Param("at") LocalDateTime at);

  /** 该订单全部来源佣金已确认后更新佣金记录。 */
  void reconcileCommissions(@Param("id") String batch, @Param("at") LocalDateTime at);

  /** 按实际到账金额和所有来源状态更新批次。 */
  void reconcileBatch(@Param("id") String batch, @Param("at") LocalDateTime at);
}
