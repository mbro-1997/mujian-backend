package com.mujian.backend.order.service;

import com.mujian.backend.common.enums.OrderParticipantRole;

import com.mujian.backend.common.enums.TradingReason;

import static com.mujian.backend.common.support.D2Values.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.mapper.ExtensionRequestMapper;
import com.mujian.backend.order.mapper.ExtensionRequestMapper.*;
import com.mujian.backend.delivery.mapper.IssuePlanMapper.Refund;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ExtensionReader {
  public record Detail(String id,String orderId,String orderNo,String factoryId,int version,String createdAt,String extensionStatus,int extraDays,
      LocalDate originalDueDate,LocalDate requestedDueDate,int rentFen,String paymentId,String paidAt,String factoryWarningAt,String factoryDeadlineAt,
      String reason,Refund refund,List<String> availableActions,String serverTime,String remark) {}
  private final ExtensionRequestMapper extensionRequestMapper;
  private final TradingAccess tradingAccess;
  private final FactoryAccess factoryAccess;
  private final VerificationAccess verificationAccess;
  private final Clock clock;

  public ExtensionReader(ExtensionRequestMapper extensionRequestMapper,TradingAccess tradingAccess,FactoryAccess factoryAccess,VerificationAccess verificationAccess,Clock clock) {
    this.extensionRequestMapper=extensionRequestMapper;this.tradingAccess=tradingAccess;this.factoryAccess=factoryAccess;this.verificationAccess=verificationAccess;this.clock=clock;
  }
  /** 确定调用者有权读取的数据范围。 */
  private Filter scope(String actor,OrderParticipantRole role,String id,String order,String status) {
    String owner=null,factoryId=null;
    switch(role) {
      case buyer -> { tradingAccess.buyer(actor);owner=actor; }
      case factory -> {
        if(!D2Codes.CODE_SUPPLIER.equals(factoryAccess.context(actor,false).roleCode())) throw ApiFailure.denied(TradingReason.SUPPLIER_ROLE_REQUIRED.name());
        factoryId=factoryAccess.profile(actor,false,false).getId();
      }
      case admin -> verificationAccess.admin(actor,"admin:extension:read");
    }
    return new Filter(id,order,owner,factoryId,status);
  }
  /** 按当前身份和筛选条件返回分页摘要。 */
  public PageResult<Detail> page(String actor,OrderParticipantRole role,String order,String status,PageQuery page) {
    ExtensionRequestMapper.Filter extensionRequestFilter=scope(actor,role,null,order,status);
    return PageResult.of(extensionRequestMapper.selectExtensionPageByQuery(extensionRequestFilter,page).stream().map(r->present(r,role)).toList(),extensionRequestMapper.selectOrderExtensionRequestCountByQuery(extensionRequestFilter),page);
  }
  /** 组装当前身份有权查看的明细与操作。 */
  public Detail detail(String actor,OrderParticipantRole role,String id) {
    List<ExtensionRequestMapper.Row> rows=extensionRequestMapper.selectExtensionPageByQuery(scope(actor,role,id,null,null),new PageQuery(1,1));
    if(rows.isEmpty())throw missing();
    return present(rows.get(0),role);
  }
  /** 转换为当前身份可读的展示数据。 */
  private Detail present(Row r,OrderParticipantRole role) {
    LocalDateTime at=now(clock);List<String> actions=new ArrayList<>();
    if(role==OrderParticipantRole.buyer && "pending_payment".equals(r.status()) && r.paymentExpireAt()!=null && at.isBefore(r.paymentExpireAt()))actions.add("pay");
    if(Set.of("pending_factory","factory_warning").contains(r.status()) && at.isBefore(r.deadlineAt())) {
      if(role==OrderParticipantRole.buyer)actions.add("cancel");
      if(role==OrderParticipantRole.factory)actions.addAll(List.of("approve","reject"));
    }
    if(r.refundId()!=null)actions.add("view_refund");
    return new Detail(r.id(),r.orderId(),r.orderNo(),r.factoryId(),r.version(),time(r.createdAt()),r.status(),r.extraDays(),r.originalDueDate(),
        r.requestedDueDate(),r.rentFen(),r.paymentId(),time(r.paidAt()),time(r.warningAt()),time(r.deadlineAt()),r.reason(),
        r.refundId()==null?null:new Refund(r.refundId(),r.refundStatus(),r.refundFen()),actions,time(at),r.remark());
  }
}
