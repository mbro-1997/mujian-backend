package com.mujian.backend.dispute.service;

import com.mujian.backend.common.enums.OrderParticipantRole;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.enums.TradingReason;
import static com.mujian.backend.common.support.D2Values.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.dispute.mapper.*;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.*;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class Receivables {
  public record Detail(String id,String orderId,String orderNo,String factoryId,int version,String createdAt,String receivableNo,String sourceType,String sourceId,
      String status,int confirmedLossFen,int depositDeductedFen,int receivableFen,int paidFen,int waivedFen,int outstandingFen,String paymentId,List<String> availableActions,String serverTime,String factoryName,Integer overdueRentDueFen,Integer overdueLiabilityDueFen,Integer overdueRentPaidFen,Integer overdueLiabilityPaidFen){}
  private final ReceivableMapper receivableMapper;
  private final TradingRoots tradingRoots;
  private final RentalOrderMapper rentalOrderMapper;
  private final TradingOperations tradingOperations;
  private final FactoryAccess factoryAccess;
  private final VerificationAccess verificationAccess;
  private final TradingAccess tradingAccess;
  private final Clock clock;
  public Receivables(ReceivableMapper receivableMapper,TradingRoots tradingRoots,RentalOrderMapper rentalOrderMapper,TradingOperations tradingOperations,FactoryAccess factoryAccess,VerificationAccess verificationAccess,TradingAccess tradingAccess,Clock clock){
    this.receivableMapper=receivableMapper;this.tradingRoots=tradingRoots;this.rentalOrderMapper=rentalOrderMapper;this.tradingOperations=tradingOperations;this.factoryAccess=factoryAccess;this.verificationAccess=verificationAccess;this.tradingAccess=tradingAccess;this.clock=clock;
  }
  /** 确定调用者有权读取的数据范围。 */
  private ClaimMapper.Filter scope(String actor,OrderParticipantRole role,String order,String status){
    String buyer=null,factory=null;
    switch(role){case buyer->{tradingAccess.buyer(actor);buyer=actor;}case admin->verificationAccess.admin(actor,"admin:compensation:read");
      case factory->{if(!D2Codes.CODE_SUPPLIER.equals(factoryAccess.context(actor,false).roleCode()))throw ApiFailure.denied(TradingReason.SUPPLIER_ROLE_REQUIRED.name());factory=factoryAccess.profile(actor,false,false).getId();}}
    return new ClaimMapper.Filter(null,order,buyer,factory,role==OrderParticipantRole.admin,status);
  }
  /** 组装当前身份有权查看的明细与操作。 */
  private Detail detail(ReceivableMapper.Row r,OrderParticipantRole role){return new Detail(r.id(),r.orderId(),r.orderNo(),r.factoryId(),r.version(),time(r.createdAt()),
      r.receivableNo(),r.sourceType(),r.sourceId(),r.status(),r.confirmedLossFen(),r.depositDeductedFen(),r.receivableFen(),r.paidFen(),r.waivedFen(),r.outstanding(),r.paymentId(),
      role==OrderParticipantRole.buyer && r.outstanding()>0 && Set.of("pending","paying").contains(r.status())?List.of("pay"):List.of(),time(now(clock)),r.factoryName(),r.overdueRentDueFen(),r.overdueLiabilityDueFen(),r.overdueRentPaidFen(),r.overdueLiabilityPaidFen());}
  /** 按当前身份和筛选条件返回分页摘要。 */
  public PageResult<Detail> page(String actor,OrderParticipantRole role,String order,String status,PageQuery pageQuery){
    ClaimMapper.Filter claimFilter=scope(actor,role,order,status);return PageResult.of(receivableMapper.selectReceivablePageByQuery(claimFilter,pageQuery).stream().map(r->detail(r,role)).toList(),receivableMapper.selectOrderCompensationReceivableCountByQuery(claimFilter),pageQuery);
  }
  /** 校验访问范围后返回业务详情。 */
  public Detail read(String actor,OrderParticipantRole role,String id){
    ClaimMapper.Filter claimFilter=scope(actor,role,null,null);ReceivableMapper.Row receivableRow=receivableMapper.selectReceivableById(id);
    if(receivableRow==null || claimFilter.buyerId()!=null && !claimFilter.buyerId().equals(receivableRow.buyerId()) || claimFilter.factoryId()!=null && !claimFilter.factoryId().equals(receivableRow.factoryId()))throw missing();
    return detail(receivableRow,role);
  }
  /** 校验当前事实并准备下一步操作。 */
  public String prepare(TradingCommand tradingCommand,String id,int version){
    tradingOperations.authorize(tradingCommand.actor(),tradingCommand.operation());
    ReceivableMapper.Row receivableRow=receivableMapper.selectReceivableById(id);if(receivableRow==null || !receivableRow.buyerId().equals(tradingCommand.actor()))throw missing();
    tradingRoots.lock(rentalOrderMapper.selectOrderById(receivableRow.orderId()).checkoutBatchId());
    ReceivableMapper.Row receivableRowRow=receivableMapper.selectReceivableById(id);
    String original=tradingOperations.replayPayment(tradingCommand);if(original!=null)return original;
    if(receivableRowRow.version()!=version)throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if(receivableRowRow.outstanding()<=0 || !Set.of("pending","paying").contains(receivableRowRow.status()))throw conflict(BusinessFailureReason.RECEIVABLE_ALREADY_SETTLED.name());
    String payment=receivableRowRow.paymentId();
    if("pending".equals(receivableRowRow.status())){
      LocalDateTime at=now(clock);payment=Ids.next();
      if(receivableMapper.createPayment(id,payment,tradingCommand.actor()+":"+tradingCommand.requestId(),at)!=1 || receivableMapper.createSub(id,payment,Ids.next(),at)!=1)throw new IllegalStateException("补缴渠道来源不完整");
      receivableMapper.updateOrderCompensationReceivableActivate(id,payment,at);
    }
    tradingOperations.recordPayment(tradingCommand,id,receivableMapper.selectReceivableById(id).version(),payment);return payment;
  }
  /** 合并外部结果并推进本地状态。 */
  @Transactional(propagation=Propagation.MANDATORY)
  public boolean observe(String payment,ChannelSuborderDO channelSuborderDO,LocalDateTime at){
    String id=receivableMapper.selectReceivableIdByPaymentId(payment);if(id==null)return false;
    if("closed".equals(channelSuborderDO.paymentStatus()))receivableMapper.updateOrderCompensationReceivableClosed(id,payment,at);
    if("success".equals(channelSuborderDO.paymentStatus()) && receivableMapper.insertPaymentAllocation(Ids.next(),channelSuborderDO.id(),at)==1){
      if(receivableMapper.updateOrderCompensationReceivablePaid(id,payment,channelSuborderDO.paidAmountFen(),channelSuborderDO.paidAt(),at)!=1)throw new IllegalStateException("赔付实收与原应收不符");
      receivableMapper.incrementOrderPaidAmount(channelSuborderDO.rentalOrderId(),channelSuborderDO.paidAmountFen(),at);
    }
    return true;
  }
}
