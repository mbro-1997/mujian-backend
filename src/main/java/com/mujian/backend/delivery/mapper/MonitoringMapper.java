package com.mujian.backend.delivery.mapper;

import com.mujian.backend.delivery.dto.MonitoringQuery;
import com.mujian.backend.delivery.vo.MonitoringViews;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MonitoringMapper {
  /** 按授权工厂及时间筛选待处理的归还核验预警。 */
  List<MonitoringViews.Warning> selectMonitoringWarnings(@Param("warnings") MonitoringQuery.Warnings warnings);

  /** 按授权工厂及时间筛选待处理的归还核验预警。 */
  long countWarnings(@Param("warnings") MonitoringQuery.Warnings warnings);

  /** 按来源和实际受影响订单筛选库存风险。 */
  List<MonitoringViews.Risk> selectMonitoringRisks(@Param("risks") MonitoringQuery.Risks risks);

  record RiskOrder(java.time.LocalDate currentDueDate, int paidDepositFen, java.time.LocalDateTime resolvedAt) {}

  /** 按 riskId 查询 order_fulfillment_risk 记录。 */
  RiskOrder selectRiskOrder(@Param("riskId") String riskId);

  /** 查询单个风险及其主来源事项。 */
  MonitoringViews.Risk selectRiskById(@Param("id") String id);

  /** 按来源和实际受影响订单筛选库存风险。 */
  long countRisks(@Param("risks") MonitoringQuery.Risks risks);
}
