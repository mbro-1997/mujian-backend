package com.mujian.backend.delivery.service;

import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.delivery.dto.MonitoringQuery;
import com.mujian.backend.delivery.mapper.MonitoringMapper;
import com.mujian.backend.delivery.vo.MonitoringViews;
import com.mujian.backend.material.service.VerificationAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FulfillmentMonitoring {
  private final MonitoringMapper monitoringMapper;
  private final VerificationAccess verificationAccess;

  /** 装配预警与风险筛选所需依赖。 */
  public FulfillmentMonitoring(
      MonitoringMapper monitoringMapper, VerificationAccess verificationAccess) {
    this.monitoringMapper = monitoringMapper;
    this.verificationAccess = verificationAccess;
  }

  /** 按授权工厂及时间筛选待处理的归还核验预警。 */
  public PageResult<MonitoringViews.Warning> warnings(
      String actor, MonitoringQuery.Warnings warnings) {
    verificationAccess.admin(actor, "admin:fulfillment:return:read");
    return PageResult.of(
        monitoringMapper.selectMonitoringWarnings(warnings), monitoringMapper.countWarnings(warnings), warnings.page());
  }

  /** 按来源和实际受影响订单筛选库存风险。 */
  public PageResult<MonitoringViews.Risk> risks(String actor, MonitoringQuery.Risks risks) {
    verificationAccess.admin(actor, "admin:fulfillment:risk:read");
    return PageResult.of(
        monitoringMapper.selectMonitoringRisks(risks), monitoringMapper.countRisks(risks), risks.page());
  }
}
