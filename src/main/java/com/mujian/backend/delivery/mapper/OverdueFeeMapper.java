package com.mujian.backend.delivery.mapper;

import org.apache.ibatis.annotations.Param;
import java.time.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OverdueFeeMapper {
  record Facts(
      String orderId,
      LocalDate dueDate,
      int deposit,
      String mode,
      LocalDateTime earliest,
      boolean returning,
      boolean closed,
      LocalDateTime dispatch,
      LocalDateTime arrival,
      String legacyMethod,
      LocalDateTime legacyAt,
      String legacyBasis,
      String feeId,
      String status,
      int version,
      String method,
      LocalDateTime handoverAt,
      String basis,
      boolean disputed,
      Long excludedMs,
      String snapshot,
      Integer rent,
      Integer liability,
      Integer rentOffset,
      Integer liabilityOffset,
      String reviewBasis,
      String receivableId,
      int rentPaid,
      int liabilityPaid) {}

  record Line(String itemId, int quantity, int dailyRentFen) {}

  /** 读取有效归还日、可追溯交接事实及已成功补缴的分摊。 */
  Facts selectFeeFactsByOrder(@Param("orderId") String orderId);

  /** 取得下单时单件日租及原数量。 */
  List<Line> selectPriceLinesByOrder(@Param("orderId") String orderId);

  /** 创建订单唯一的归还费用来源。 */
  void insertFeeSource(@Param("id") String id, @Param("orderId") String orderId, @Param("dueDate") LocalDate dueDate, @Param("status") String status, @Param("now") LocalDateTime now);

  /** 保存首次实际交接依据，保留原填报人。 */
  int recordReportedHandover(@Param("orderId") String orderId,
      @Param("method") String method,
      @Param("handoverAt") LocalDateTime handoverAt,
      @Param("basis") String basis,
      @Param("actor") String actor,
      @Param("disputed") boolean disputed,
      @Param("now") LocalDateTime now);

  /** 按原版本固化生效金额及审核依据。 */
  int confirmFeeAssessment(@Param("orderId") String orderId,
      @Param("version") int version,
      @Param("method") String method,
      @Param("handoverAt") LocalDateTime handoverAt,
      @Param("excludedMs") long excludedMs,
      @Param("days") int days,
      @Param("snapshot") String snapshot,
      @Param("rent") int rent,
      @Param("liability") int liability,
      @Param("rentOffset") int rentOffset,
      @Param("liabilityOffset") int liabilityOffset,
      @Param("basis") String basis,
      @Param("actor") String actor,
      @Param("fault") boolean fault,
      @Param("now") LocalDateTime now);

  /** 登记对应费用类型的押金抵用流水。 */
  void insertDepositOffset(@Param("id") String id,
      @Param("orderId") String orderId,
      @Param("source") String source,
      @Param("kind") String kind,
      @Param("amount") int amount,
      @Param("basis") String basis,
      @Param("actor") String actor,
      @Param("now") LocalDateTime now);

  /** 将两项不足额合入原补缴入口。 */
  void insertGapReceivable(@Param("id") String id, @Param("orderId") String orderId, @Param("available") int available, @Param("now") LocalDateTime now);
}
