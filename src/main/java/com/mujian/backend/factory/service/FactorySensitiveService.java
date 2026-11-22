package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.domain.FactoryChangeSnapshot;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.projection.*;
import org.springframework.stereotype.Service;

@Service
public class FactorySensitiveService {
  private final FactoryAccess factoryAccess;
  private final FactoryReviewMapper factoryReviewMapper;
  private final FactorySnapshotData factorySnapshotData;
  private final FactoryAudit factoryAudit;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactorySensitiveService(
      FactoryAccess factoryAccess,
      FactoryReviewMapper factoryReviewMapper,
      FactorySnapshotData factorySnapshotData,
      FactoryAudit factoryAudit) {
    this.factoryAccess = factoryAccess;
    this.factoryReviewMapper = factoryReviewMapper;
    this.factorySnapshotData = factorySnapshotData;
    this.factoryAudit = factoryAudit;
  }

  /** 组装已授权的敏感资料响应。 */
  private SensitiveResult result(
      String actor,
      FactorySource factorySource,
      String id,
      FactorySnapshot factorySnapshot,
      String trace,
      String section) {
    String auditId =
        factoryAudit.record(
            actor,
            factorySource.table,
            id,
            "factory_sensitive_view",
            "admin:" + factorySource.permission + ":sensitive",
            trace,
            null,
            null,
            null,
            null,
            null,
            com.mujian.backend.common.support.D2Values.view("section", section));
    return new SensitiveResult(
        factorySnapshot.contact_name(), factorySnapshot.contact_phone(), auditId);
  }

  /** 检查材料访问权限后返回短时签名。 */
  public SensitiveResult view(String actor, FactorySource factorySource, String id, String trace) {
    factoryAccess.admin(actor, factorySource.permission, "sensitive");
    FactorySourceProjection factorySourceProjection =
        factoryReviewMapper.selectFactorySourceById(factorySource, id(id), false);
    if (factorySourceProjection == null) throw missing();
    return result(
        actor,
        factorySource,
        id,
        factorySnapshotData.read(factorySourceProjection),
        trace,
        "source");
  }

  /** 读取或规范化工厂资料变更，保留冻结快照结构。 */
  public ChangeSensitiveResult change(String actor, String id, String trace) {
    factoryAccess.admin(actor, FactorySource.CHANGE.permission, "sensitive");
    FactorySourceProjection factorySourceProjection =
        factoryReviewMapper.selectFactorySourceById(FactorySource.CHANGE, id(id), false);
    if (factorySourceProjection == null) throw missing();
    FactoryChangeSnapshot factoryChangeSnapshot =
        factorySnapshotData.change(factorySourceProjection);
    FactorySourceProjection factorySourceProjectionCurrent =
        factoryReviewMapper.selectFactorySourceById(
            FactorySource.PROFILE, factorySourceProjection.getFactoryId(), false);
    return new ChangeSensitiveResult(
        result(actor, FactorySource.CHANGE, id, factoryChangeSnapshot.before(), trace, "before"),
        result(
            actor, FactorySource.CHANGE, id, factoryChangeSnapshot.proposed(), trace, "proposed"),
        result(
            actor,
            FactorySource.CHANGE,
            id,
            factorySnapshotData.read(factorySourceProjectionCurrent),
            trace,
            "current"));
  }
}
