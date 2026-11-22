package com.mujian.backend.delivery.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OverdueMapper {
  record Candidate(
      String reservationId,
      String orderId,
      String itemId,
      String skuId,
      String snId,
      int sourceQuantity,
      LocalDate startDate,
      LocalDate endDate) {}

  record Event(String id, String orderId, LocalDateTime eventTime) {}

  record RiskSource(String id,String sourceId,String affectedId,String skuId,String snId,String status,
      String sourceStatus,String affectedStatus,LocalDate startDate,LocalDate endDate) {}

  /** 每次最多取一百条待核实的已有风险。 */
  List<String> selectActiveRiskIds(@Param("after") String after);

  /** 原订单、原占用及被影响明细共同决定解除依据。 */
  RiskSource selectRiskSource(@Param("id") String id);

  /** 核实来源后同时关闭风险和仍活动的管理事项。 */
  int resolveRiskBySource(@Param("id") String id,@Param("at") LocalDateTime at,@Param("reason") String reason,
      @Param("systemId") String systemId);

  /** 按上海租期截止日分页领取仍在租订单。 */
  List<String> selectDue(@Param("today") LocalDate today, @Param("after") String after);

  /** 仅将当前仍在租的本单占用转为逾期持有。 */
  int markOverdue(@Param("orderId") String orderId, @Param("at") LocalDateTime at);

  /** 分页领取尚未完成影响分析的逾期事件。 */
  List<String> selectPending(@Param("after") String after);

  /** 按事件号读取原订单，在消费事务内加锁。 */
  Event selectEvent(@Param("id") String id, @Param("lock") boolean lock);

  /** 按相同 SN 或 SKU 原占用区间取得受影响候选。 */
  List<Candidate> selectCandidates(@Param("source") String source, @Param("today") LocalDate today);

  /** 按原租赁订单加锁，串行处理互斥动作。 */
  String lockOrder(@Param("id") String id);

  /** 按来源、实际受影响占用和资源唯一写入风险。 */
  void insertRisk(
      @Param("id") String id,
      @Param("key") String key,
      @Param("source") String source,
      @Param("row") Candidate row,
      @Param("at") LocalDateTime at);

  /** 按固定影响键找到已有风险来源。 */
  String selectRiskIdByKey(@Param("key") String key);

  /** 所有影响处理成功后才记录事件消费完成。 */
  int markProcessed(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 查询当前订单是否有生效的供应预警。 */
  boolean hasActiveRisk(@Param("orderId") String orderId);
}
