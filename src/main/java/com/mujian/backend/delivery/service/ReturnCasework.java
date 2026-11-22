package com.mujian.backend.delivery.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.dto.CloseCaseDTO;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.domain.OrderReturnCheckDO;
import com.mujian.backend.order.domain.RentalOrderDO;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 08复用原案件/动作和来源关联，每个固定步骤保存实际领域结果。 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ReturnCasework {
  public record Step(
      String module,
      String operation,
      String targetType,
      String targetId,
      List<String> resultIds) {}

  public record Closure(String checkId, String reason, List<Step> steps) {}

  private final AdminCaseMapper adminCaseMapper;
  private final AdminCaseActionMapper adminCaseActionMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;
  private final ObjectMapper objectMapper;

  /** 复用已上线案件唯一守卫及结案版本条件。 */
  public ReturnCasework(
      AdminCaseMapper adminCaseMapper,
      AdminCaseActionMapper adminCaseActionMapper,
      AdminCaseRelationMapper adminCaseRelationMapper,
      ObjectMapper objectMapper) {
    this.adminCaseMapper = adminCaseMapper;
    this.adminCaseActionMapper = adminCaseActionMapper;
    this.adminCaseRelationMapper = adminCaseRelationMapper;
    this.objectMapper = objectMapper;
  }

  /** 在订单与核验锁内按核验来源查建唯一预警案件。 */
  public AdminCaseDO find(
      RentalOrderDO order, OrderReturnCheckDO check, boolean create, LocalDateTime at) {
    List<AdminCaseDO> rows =
        adminCaseMapper.selectAdminCasesBySource("order_return_check", check.id(), true);
    if (rows.size() > 1) throw new IllegalStateException("return case duplicated");
    if (!rows.isEmpty()) return rows.get(0);
    if (!create) return null;
    String id = Ids.next();
    AdminCaseDO adminCaseDO =
        new AdminCaseDO()
            .id(id)
            .caseNo("CASE" + id)
            .activeCaseKey("return_check:" + check.id())
            .caseType("return_check_timeout")
            .caseStatus("pending")
            .priority("P2")
            .title("归还核验待处理")
            .buyerUserId(order.buyerUserId())
            .factoryId(order.factoryId())
            .rentalOrderId(order.id())
            .deadlineAt(check.autoCloseAt())
            .createdByType(FulfillmentRole.system.name())
            .createdByAdminUserId(SystemIdentity.SYSTEM_USER_ID)
            .version(0)
            .createdAt(at)
            .updatedAt(at);
    adminCaseMapper.insertAdminCase(adminCaseDO);
    adminCaseRelationMapper.insertAdminCaseRelation(
        new AdminCaseRelationDO()
            .id(Ids.next())
            .adminCaseId(id)
            .relationType("order_return_check")
            .relationId(check.id())
            .relationRole("source")
            .isPrimary(1)
            .createdAt(at));
    return adminCaseDO;
  }

  /** 以原结案凭据内的两个事件ID修复缺失行，保留已经交付的追溯链接。 */
  public List<String> recordedEvents(AdminCaseDO adminCaseDO, String checkId) {
    if (adminCaseDO == null || adminCaseDO.getClosureChecklistJson() == null || !"closed".equals(adminCaseDO.getCaseStatus()))
      return List.of();
    Closure closure = objectMapper.readValue(adminCaseDO.getClosureChecklistJson(), Closure.class);
    if (checkId.equals(closure.checkId()))
      for (Step step : closure.steps()) {
        if ("record_return_event".equals(step.operation()) && step.resultIds().size() == 2)
          return step.resultIds();
      }
    throw new IllegalStateException("return closure event references missing");
  }

  /** 一次数据库事务提交的执行链，步骤ID和结果与业务事实一起可见。 */
  public String finish(
      AdminCaseDO adminCaseDO,
      Closure closure,
      String actor,
      String request,
      FulfillmentRole role,
      LocalDateTime at) {
    if (adminCaseDO == null) return null;
    String parent = null;
    if (role != FulfillmentRole.factory) {
      parent = adminCaseActionMapper.selectReturnParent(adminCaseDO.getId(), closure.checkId());
      if (parent == null) {
        parent = Ids.next();
        action(
            adminCaseDO,
            parent,
            null,
            null,
            "05",
            "close_return_check",
            "order_return_check",
            closure.checkId(),
            request,
            actor,
            closure.reason(),
            objectMapper.writeValueAsString(closure),
            at);
      }
      List<Integer> finished = adminCaseActionMapper.selectReturnSteps(parent);
      int step = 0;
      for (Step fact : closure.steps()) {
        step++;
        if (finished.contains(step)) continue;
        action(
            adminCaseDO,
            Ids.next(),
            parent,
            step,
            fact.module(),
            fact.operation(),
            fact.targetType(),
            fact.targetId(),
            "return:" + closure.checkId() + ":" + step,
            actor,
            closure.reason(),
            objectMapper.writeValueAsString(fact),
            at);
      }
    }
    if ("closed".equals(adminCaseDO.getCaseStatus())) return parent;
    if (adminCaseMapper.closeAdminCaseByVersion(
            new CloseCaseDTO()
                .id(adminCaseDO.getId())
                .version(adminCaseDO.getVersion())
                .status("normal_return_closed")
                .summary(closure.reason())
                .checklist(objectMapper.writeValueAsString(closure))
                .actor(actor)
                .now(at))
        != 1) throw new IllegalStateException("return case changed");
    return parent;
  }

  /** 真实动作和模块目标，不生成虚拟管理员或第二套工单。 */
  private void action(
      AdminCaseDO adminCaseDO,
      String id,
      String parent,
      Integer step,
      String module,
      String operation,
      String targetType,
      String targetId,
      String key,
      String actor,
      String reason,
      String result,
      LocalDateTime at) {
    adminCaseActionMapper.insertAdminCaseAction(
        new AdminCaseActionDO()
            .id(id)
            .actionNo("ACT" + id)
            .adminCaseId(adminCaseDO.getId())
            .parentActionId(parent)
            .stepNo(step)
            .actionType(operation)
            .actionStatus("succeeded")
            .targetModule(module)
            .targetType(targetType)
            .targetId(targetId)
            .idempotencyKey(key)
            .operatorAdminUserId(actor)
            .operationReason(reason)
            .requestPayloadJson(objectMapper.writeValueAsString(new Request(key, reason)))
            .resultPayloadJson(result)
            .operatorConfirmedAt(at)
            .executedAt(at)
            .createdAt(at)
            .updatedAt(at));
  }

  private record Request(String requestId, String reason) {}
}
