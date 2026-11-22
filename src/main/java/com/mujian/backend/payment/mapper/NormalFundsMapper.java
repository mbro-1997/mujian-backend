package com.mujian.backend.payment.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NormalFundsMapper {
  record Allocation(
      String id,
      String paymentId,
      String suborderId,
      int rent,
      int deposit,
      int freight,
      int other,
      int total) {}

  /** 实际分摊按原子单排序，未知资金科目不得混入正常退押/核销。 */
  List<Allocation> selectAllocations(@Param("orderId") String orderId);

  /** 两程均非平台池且原预收确为零，账户确认为无需处理。 */
  int settleNoPool(@Param("orderId") String orderId, @Param("at") LocalDateTime at);

  /** 无押金或已有义务分别写明责任，而非把待执行当成到账。 */
  int updateDepositResponsibility(@Param("orderId") String orderId, @Param("at") LocalDateTime at);

  /** 核实本地核验、库存、押金、运费及退款事实齐备后才完成。 */
  int tryComplete(@Param("orderId") String orderId, @Param("at") LocalDateTime at);

  /** 区分仍等待渠道与缺失本地关闭步骤。 */
  boolean requiresClosureRepair(@Param("orderId") String orderId);

  /** 重启或门禁恢复后重新评估遗留已关闭订单。 */
  List<String> selectCompletionCandidates(@Param("afterId") String afterId);
}
