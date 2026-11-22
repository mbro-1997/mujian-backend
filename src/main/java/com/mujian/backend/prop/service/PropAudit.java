package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.prop.utils.PropJson;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class PropAudit {
  private final PropCaseGateway propCaseGateway;
  private final PropJson propJson;
  private final Clock clock;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropAudit(PropCaseGateway propCaseGateway, PropJson propJson, Clock clock) {
    this.propCaseGateway = propCaseGateway;
    this.propJson = propJson;
    this.clock = clock;
  }

  /** 把当前操作和业务来源写入既有管理员审计记录。 */
  public void record(
      String actor,
      String type,
      String id,
      String op,
      String permission,
      String trace,
      String request,
      String caseId,
      String action,
      String reason,
      Object before,
      Object after) {
    String key = Ids.next();
    propCaseGateway.insertAudit(
        new com.mujian.backend.prop.dto.PropCaseDTO.CreateAudit(
            key,
            "AUD" + key,
            actor,
            caseId,
            action,
            permission,
            op,
            op,
            "02",
            type,
            id,
            trace,
            request,
            reason,
            before == null ? null : propJson.write(before),
            after == null ? null : propJson.write(after),
            "success",
            now(clock)));
  }
}
