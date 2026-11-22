package com.mujian.backend.dispute.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import java.util.ArrayList;
import static com.mujian.backend.common.support.D2Values.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.dispute.mapper.DisputeFundsMapper;
import java.time.LocalDateTime;
import com.mujian.backend.delivery.mapper.IssuePlanMapper;
import com.mujian.backend.payment.dto.CashComponents;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.payment.service.RefundObligations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class DisputeFunds {
  private final com.mujian.backend.delivery.service.OverdueFees overdueFees;
  private final DisputeFundsMapper disputeFundsMapper;
  private final IssuePlanMapper issuePlanMapper;
  private final RefundObligations obligations;
  private final PaymentOrderMapper paymentOrderMapper;
  public DisputeFunds(DisputeFundsMapper disputeFundsMapper,IssuePlanMapper issuePlanMapper,RefundObligations obligations,PaymentOrderMapper paymentOrderMapper,com.mujian.backend.delivery.service.OverdueFees overdueFees){
    this.overdueFees=overdueFees;
    this.disputeFundsMapper=disputeFundsMapper;this.issuePlanMapper=issuePlanMapper;this.obligations=obligations;this.paymentOrderMapper=paymentOrderMapper;
  }
  /** 按资金来源登记对应的退款义务。 */
  @Transactional(propagation=Propagation.MANDATORY)
  public java.util.List<String> refund(String order,String sourceType,String source,CashComponents amount,String reason,String actor) {
    int rent=amount.rentFen(),deposit=amount.depositFen(),freight=amount.freightFen();
    if(deposit>0)overdueFees.requireResolved(order);
    ArrayList<String> ids=new java.util.ArrayList<String>();
    if(rent==0 && deposit==0 && freight==0)return ids;
    for(IssuePlanMapper.Source issuePlanSource:issuePlanMapper.selectRefundFundingSourcesByOrder(order,null)){
      CashComponents part=new CashComponents(Math.min(rent,issuePlanSource.rent()),Math.min(deposit,issuePlanSource.deposit()),Math.min(freight,issuePlanSource.freight()));
      if(part.totalFen()==0)continue;
      ids.add(obligations.reserve(actor,"rental_order",order,sourceType,source,"order_claim".equals(sourceType)?"deposit_release":"arbitration_refund",reason,
        paymentOrderMapper.lockSuborderById(issuePlanSource.suborderId()),new CashComponents(issuePlanSource.originalRent(),issuePlanSource.originalDeposit(),issuePlanSource.originalFreight()),part));
      rent-=part.rentFen();deposit-=part.depositFen();freight-=part.freightFen();
    }
    if(rent+((long)deposit)+freight!=0)throw conflict(BusinessFailureReason.REFUND_LIMIT_CHANGED.name());
    return ids;
  }
  /** 读取当前仍可处置的押金金额。 */
  public int deposit(String order){return disputeFundsMapper.selectAvailableOrderFundsBySubject(order,"deposit");}
  /** 调用者持有订单锁，同一生效索赔回应或已确认裁决的资金步骤只提交一次。 */
  @Transactional(propagation=Propagation.MANDATORY)
  public String effective(String order,String type,String source,int loss,int rent,String reason,LocalDateTime at){
    if(loss>0)overdueFees.requireResolved(order);
    int available=deposit(order),deduction=Math.min(loss,available);
    if(rent>disputeFundsMapper.selectAvailableOrderFundsBySubject(order,"rent"))throw conflict(BusinessFailureReason.REFUND_LIMIT_CHANGED.name());
    if(deduction>0)disputeFundsMapper.insertOrderDeduction(Ids.next(),order,type,source,"deposit",deduction,reason,SystemIdentity.SYSTEM_USER_ID,at);
    if(rent>0)disputeFundsMapper.insertOrderDeduction(Ids.next(),order,type,source,"rent",rent,reason,SystemIdentity.SYSTEM_USER_ID,at);
    String receivable=null;
    if(loss>deduction){receivable=Ids.next();disputeFundsMapper.insertOrderCompensationReceivable(receivable,order,type,source,loss,available,deduction,at);}
    disputeFundsMapper.updateRentalOrderTotals(order,at);return receivable;
  }
}
