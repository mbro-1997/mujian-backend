package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.AdminOperationAuditDO;
import com.mujian.backend.casework.mapper.AdminOperationAuditMapper;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.Ids;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 管理员敏感交易读取留痕，只登记字段类别与资源，不复制地址电话或渠道原文。 */
@Service
public class TradeReadAudit {
  public record Snapshot(
      int schemaVersion, String resourceType, String resourceId, List<String> fieldTypes) {}

  private final AdminOperationAuditMapper adminOperationAuditMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public TradeReadAudit(
      AdminOperationAuditMapper adminOperationAuditMapper, ObjectMapper objectMapper, Clock clock) {
    this.adminOperationAuditMapper = adminOperationAuditMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 记录敏感详情访问的最小审计，不复制地址或渠道原文。 */
  public void record(String actor, String permission, String type, String id, String trace) {
    com.mujian.backend.order.service.TradeReadAudit.Snapshot snapshot =
        new Snapshot(1, type, id, List.of("trade_snapshot", "funds"));
    adminOperationAuditMapper.insertAdminOperationAudit(
        new AdminOperationAuditDO()
            .id(Ids.next())
            .auditNo("TRADE-" + Ids.next())
            .adminUserId(actor)
            .permissionCode(permission)
            .operationCode("trade_detail_read")
            .operationName("交易详情查询")
            .targetModule("04")
            .targetType(type)
            .targetId(id)
            .requestId(trace)
            .idempotencyKey("trade-read:" + trace)
            .afterSnapshotJson(objectMapper.writeValueAsString(snapshot))
            .resultStatus(TradingState.success.name())
            .createdAt(now(clock)));
  }
}
