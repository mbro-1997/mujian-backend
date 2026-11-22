package com.mujian.backend.planning.service;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Codes.CODE_ACTIVE;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.planning.domain.PlanningOperationDO;
import com.mujian.backend.planning.dto.PlanningCommand;
import com.mujian.backend.planning.mapper.PlanningOperationMapper;
import com.mujian.backend.planning.vo.PlanningViews.Receipt;
import com.mujian.backend.prop.policy.PropAccess;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 本批用户根锁和持久回执；不承接其他业务的幂等记录。 */
@Service
public class PlanningOperations {
  private final PlanningOperationMapper planningOperationMapper;
  private final FactoryAccess factoryAccess;
  private final PropAccess propAccess;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 注入既有身份、根锁及序列化能力。 */
  public PlanningOperations(
      PlanningOperationMapper planningOperationMapper,
      FactoryAccess factoryAccess,
      PropAccess propAccess,
      ObjectMapper objectMapper,
      Clock clock) {
    this.planningOperationMapper = planningOperationMapper;
    this.factoryAccess = factoryAccess;
    this.propAccess = propAccess;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 每次读取核对租赁方身份；受限用户可读取。 */
  public boolean readable(String actor) {
    propAccess.demander(actor);
    return CODE_ACTIVE.equals(factoryAccess.context(actor, false).userStatus());
  }

  /** 按统一顺序取得用户根锁；随后由调用者核对对象归属再查回执。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(String actor) {
    propAccess.demander(actor);
    factoryAccess.lock(actor);
  }

  /** 新命令才执行账号写限制，回放必须先于此检查。 */
  public void writable(String actor) {
    factoryAccess.context(actor, true);
  }

  /** 在已有事务里查原键，异内容拒绝，旧版本和软删不影响回放。 */
  public Receipt replay(PlanningCommand planningCommand) {
    PlanningOperationDO planningOperationDO =
        planningOperationMapper.selectPlanningOperationByActorAndRequestId(
            planningCommand.actor(), planningCommand.requestId());
    if (planningOperationDO == null) return null;
    if (!planningOperationDO.requestHash().equals(planningCommand.hash()))
      throw conflict(D2Codes.CODE_IDEMPOTENCY_KEY_REUSED);
    Receipt receipt = objectMapper.readValue(planningOperationDO.resultJson(), Receipt.class);
    return new Receipt(
        receipt.requestId(),
        receipt.operation(),
        receipt.resourceType(),
        receipt.resourceId(),
        receipt.resourceVersion(),
        "replayed",
        receipt.appliedAt());
  }

  /** 同业务事务保存不可变回执。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public Receipt record(
      PlanningCommand planningCommand,
      PlanningResource planningResourceResource,
      String id,
      Integer version) {
    Receipt receipt =
        new Receipt(
            planningCommand.requestId(),
            planningCommand.operation().name(),
            planningResourceResource.name(),
            id,
            version,
            "applied",
            time(now(clock)));
    planningOperationMapper.insertPlanningOperation(
        new PlanningOperationDO(
            Ids.next(),
            planningCommand.actor(),
            planningCommand.requestId(),
            planningCommand.operation().name(),
            planningCommand.hash(),
            planningResourceResource.name(),
            id,
            objectMapper.writeValueAsString(receipt),
            now(clock)));
    return receipt;
  }

  /** 原请求恢复仅返回原事实，不用旧版本覆盖当前详情。 */
  public Receipt recover(String actor, String requestId) {
    readable(actor);
    PlanningOperationDO planningOperationDO =
        planningOperationMapper.selectPlanningOperationByActorAndRequestId(actor, requestId);
    if (planningOperationDO == null) throw missing();
    Receipt receipt = objectMapper.readValue(planningOperationDO.resultJson(), Receipt.class);
    return new Receipt(
        receipt.requestId(),
        receipt.operation(),
        receipt.resourceType(),
        receipt.resourceId(),
        receipt.resourceVersion(),
        "recovered",
        receipt.appliedAt());
  }
}
