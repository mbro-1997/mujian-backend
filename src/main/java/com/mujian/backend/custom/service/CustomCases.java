package com.mujian.backend.custom.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.custom.mapper.CustomOrderMapper.Order;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class CustomCases {
  private final AdminCaseMapper adminCaseMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;

  public CustomCases(AdminCaseMapper adminCaseMapper, AdminCaseRelationMapper adminCaseRelationMapper) {
    this.adminCaseMapper = adminCaseMapper;
    this.adminCaseRelationMapper = adminCaseRelationMapper;
  }

  /** 创建与业务来源关联的处理记录。 */
  public String open(Order o, String source, String id, LocalDateTime at) {
    boolean extension = "custom_order_extension_request".equals(source);
    String caseId = Ids.next();
    adminCaseMapper.insertAdminCase(
        new AdminCaseDO()
            .id(caseId)
            .caseNo("CC" + caseId)
            .caseType(extension ? "custom_fulfillment" : "custom_after_sale")
            .activeCaseKey(source + ":" + id)
            .title(extension ? "定制交付延期" : "定制售后办理")
            .priority("P2")
            .caseStatus("pending")
            .buyerUserId(o.buyerId())
            .factoryId(o.factoryId())
            .createdByType(FulfillmentRole.system.name())
            .createdByAdminUserId(SystemIdentity.SYSTEM_USER_ID)
            .version(0)
            .createdAt(at)
            .updatedAt(at));
    adminCaseRelationMapper.insertAdminCaseRelation(
        new AdminCaseRelationDO()
            .id(Ids.next())
            .adminCaseId(caseId)
            .relationType(source)
            .relationId(id)
            .relationRole("source")
            .isPrimary(1)
            .createdAt(at));
    adminCaseRelationMapper.insertAdminCaseRelation(
        new AdminCaseRelationDO()
            .id(Ids.next())
            .adminCaseId(caseId)
            .relationType("custom_order")
            .relationId(o.id())
            .relationRole("source")
            .isPrimary(0)
            .createdAt(at));
    return caseId;
  }
}
