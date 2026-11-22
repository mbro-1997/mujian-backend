package com.mujian.backend.dispute.mapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;
@Mapper
public interface DisputeFundsMapper {
  /** 按 order, subject 查询 refund_order 记录。 */
  int selectAvailableOrderFundsBySubject(@Param("order") String order,@Param("subject") String subject);
  /** 按 id, order, type 新增 order_deduction 记录。 */
  void insertOrderDeduction(@Param("id") String id,@Param("order") String order,@Param("type") String type,@Param("source") String source,@Param("subject") String subject,
      @Param("amount") int amount,@Param("reason") String reason,@Param("actor") String actor,@Param("at") LocalDateTime at);
  /** 按 id, order, type 新增 order_compensation_receivable 记录。 */
  void insertOrderCompensationReceivable(@Param("id") String id,@Param("order") String order,@Param("type") String type,@Param("source") String source,@Param("loss") int loss,
      @Param("available") int available,@Param("deduction") int deduction,@Param("at") LocalDateTime at);
  /** 按 order, at 更新 rental_order 记录。 */
  void updateRentalOrderTotals(@Param("order") String order,@Param("at") LocalDateTime at);
}
