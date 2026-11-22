package com.mujian.backend.casework.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.casework.domain.AdminCaseDO;
import com.mujian.backend.casework.domain.AdminCaseRelationDO;
import com.mujian.backend.casework.mapper.AdminCaseMapper;
import com.mujian.backend.casework.mapper.AdminCaseRelationMapper;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.order.domain.RentalOrderDO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentRiskCase {
  private final AdminCaseMapper adminCaseMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;

  /** 复用原管理事项和来源关联。 */
  public FulfillmentRiskCase(AdminCaseMapper adminCaseMapper, AdminCaseRelationMapper adminCaseRelationMapper) {
    this.adminCaseMapper=adminCaseMapper;
    this.adminCaseRelationMapper=adminCaseRelationMapper;
  }

  /** 只在来源事件事务内建案，两单均可在沟通时引用。 */
  @Transactional(propagation=Propagation.MANDATORY)
  public void register(String riskId,RentalOrderDO source,String affectedId,LocalDateTime at) {
    if (!adminCaseMapper.selectAdminCasesBySource("fulfillment_risk",riskId,true).isEmpty()) return;
    String caseId=Ids.next();
    adminCaseMapper.insertAdminCase(new AdminCaseDO().id(caseId).caseNo("RISK"+caseId)
        .caseType("fulfillment_risk").activeCaseKey("fulfillment_risk:"+riskId)
        .title("逾期影响后续订单").priority("P2").caseStatus("pending")
        .rentalOrderId(source.id()).buyerUserId(source.buyerUserId()).factoryId(source.factoryId())
        .createdByType(FulfillmentRole.system.name()).createdByAdminUserId(SystemIdentity.SYSTEM_USER_ID)
        .version(0).createdAt(at).updatedAt(at));
    adminCaseRelationMapper.insertAdminCaseRelation(new AdminCaseRelationDO().id(Ids.next()).adminCaseId(caseId)
        .relationType("fulfillment_risk").relationId(riskId).relationRole("source").isPrimary(1).createdAt(at));
    adminCaseRelationMapper.insertAdminCaseRelation(new AdminCaseRelationDO().id(Ids.next()).adminCaseId(caseId)
        .relationType("rental_order").relationId(source.id()).relationRole("source").isPrimary(0).createdAt(at));
    adminCaseRelationMapper.insertAdminCaseRelation(new AdminCaseRelationDO().id(Ids.next()).adminCaseId(caseId)
        .relationType("rental_order").relationId(affectedId).relationRole("affected").isPrimary(0).createdAt(at));
  }
}
