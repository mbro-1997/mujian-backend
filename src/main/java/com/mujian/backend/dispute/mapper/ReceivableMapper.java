package com.mujian.backend.dispute.mapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import com.mujian.backend.common.api.PageQuery;
@Mapper
public interface ReceivableMapper {
  record Row(String id,String orderId,String orderNo,String factoryId,String buyerId,int version,LocalDateTime createdAt,String receivableNo,
      String sourceType,String sourceId,String status,int confirmedLossFen,int depositDeductedFen,int receivableFen,int paidFen,int waivedFen,String paymentId,String factoryName,Integer overdueRentDueFen,Integer overdueLiabilityDueFen,Integer overdueRentPaidFen,Integer overdueLiabilityPaidFen) {
    /** 计算扣除已收款和已豁免金额后的待收余额。 */
    public int outstanding(){return receivableFen-paidFen-waivedFen;}
  }
  /** 按指定条件读取应收款记录。 */
  Row selectReceivableById(@Param("id") String id);
  /** 按筛选条件分页读取应收款记录。 */
  List<Row> selectReceivablePageByQuery(@Param("claimFilter") ClaimMapper.Filter claimFilter,@Param("pageQuery") PageQuery pageQuery);
  /** 按 claimFilter 统计 order_compensation_receivable 记录。 */
  long selectOrderCompensationReceivableCountByQuery(@Param("claimFilter") ClaimMapper.Filter claimFilter);
  /** 按指定条件读取应收款记录。 */
  String selectReceivableIdByPaymentId(@Param("payment") String payment);
  /** 按 id, payment, key 新增 payment_order 记录。 */
  int createPayment(@Param("id") String id,@Param("payment") String pay,@Param("key") String key,@Param("at") LocalDateTime at);
  /** 按 id, payment, sub 新增 payment_channel_suborder 记录。 */
  int createSub(@Param("id") String id,@Param("payment") String pay,@Param("sub") String sub,@Param("at") LocalDateTime at);
  /** 按 id, payment, at 更新 order_compensation_receivable 记录。 */
  void updateOrderCompensationReceivableActivate(@Param("id") String id,@Param("payment") String pay,@Param("at") LocalDateTime at);
  /** 按 id, paymentId, at 更新 order_compensation_receivable 记录。 */
  void updateOrderCompensationReceivableClosed(@Param("id") String id,@Param("paymentId") String paymentId,@Param("at") LocalDateTime at);
  /** 按 id, sub, at 新增 payment_allocation 记录。 */
  int insertPaymentAllocation(@Param("id") String id,@Param("sub") String sub,@Param("at") LocalDateTime at);
  /** 按 id, paymentId, amount 更新 order_compensation_receivable 记录。 */
  int updateOrderCompensationReceivablePaid(@Param("id") String id,@Param("paymentId") String paymentId,@Param("amount") int amount,@Param("paidAt") LocalDateTime paidAt,@Param("at") LocalDateTime at);
  /** 按 order, amount, at 更新 rental_order 记录。 */
  void incrementOrderPaidAmount(@Param("order") String order,@Param("amount") int amount,@Param("at") LocalDateTime at);
}
