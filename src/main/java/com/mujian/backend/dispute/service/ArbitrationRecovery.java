package com.mujian.backend.dispute.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.payment.domain.RefundOrderDO;
import static com.mujian.backend.common.support.D2Values.*;
import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.delivery.service.ReceiptTimerRecovery;
import com.mujian.backend.dispute.dto.ArbitrationInput;
import com.mujian.backend.dispute.mapper.*;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.payment.mapper.RefundMapper;
import java.time.*;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class ArbitrationRecovery {
  private final ArbitrationMapper arbitrationMapper;
  private final ClaimMapper claimMapper;
  private final ClaimStock claimStock;
  private final RefundMapper refundMapper;
  private final TradingRoots tradingRoots;
  private final RentalOrderMapper rentalOrderMapper;
  private final ObjectMapper json;
  private final Clock clock;
  private final ReceiptTimerRecovery timers;
  public ArbitrationRecovery(ArbitrationMapper arbitrationMapper,ClaimMapper claimMapper,ClaimStock claimStock,RefundMapper refundMapper,TradingRoots tradingRoots,RentalOrderMapper rentalOrderMapper,ObjectMapper json,
      Clock clock,ReceiptTimerRecovery timers){this.arbitrationMapper=arbitrationMapper;this.claimMapper=claimMapper;this.claimStock=claimStock;this.refundMapper=refundMapper;this.tradingRoots=tradingRoots;this.rentalOrderMapper=rentalOrderMapper;this.json=json;this.clock=clock;this.timers=timers;}
  /** 核对操作者范围并按业务根加锁。 */
  private ArbitrationMapper.Row lock(String id){
    ArbitrationMapper.Row arbitrationRow=arbitrationMapper.selectArbitrationById(id);tradingRoots.lock(rentalOrderMapper.selectOrderById(arbitrationRow.orderId()).checkoutBatchId());return arbitrationMapper.selectArbitrationById(id);
  }
  /** 记录失败原因并保留后续恢复所需的状态。 */
  public void failed(String id){
    ArbitrationMapper.Row arbitrationRow=lock(id);if(!"executing".equals(arbitrationRow.status()))return;
    ArbitrationMapper.Decision arbitrationDecision=arbitrationMapper.selectArbitrationDecision(id);arbitrationMapper.actionState(arbitrationMapper.selectAdminCaseActionParent(arbitrationDecision.id()),"failed","处置步骤尚未完成，系统将按原记录核对",now(clock));arbitrationMapper.updateArbitrationCaseStage(id,"execution_failed",now(clock));
  }
  /** 逐次独立提交，保留成功子步骤；渠道在途时等待原号执行器。 */
  public boolean next(String id){
    ArbitrationMapper.Row arbitrationRow=lock(id);
    if(!java.util.Set.of("executing","execution_failed").contains(arbitrationRow.status()))return false;
    ArbitrationMapper.Decision arbitrationDecision=arbitrationMapper.selectArbitrationDecision(id);String parent=arbitrationMapper.selectAdminCaseActionParent(arbitrationDecision.id());LocalDateTime at=now(clock);
    for(ArbitrationMapper.Step arbitrationStep:arbitrationMapper.selectAdminCaseActionSteps(parent)){
      if("succeeded".equals(arbitrationStep.status()))continue;
      if("arbitration_refund".equals(arbitrationStep.kind())){
        RefundOrderDO refund=refundMapper.selectRefundById(arbitrationStep.target(),true);
        if("failed".equals(refund.refundStatus())){
          if(!"failed".equals(arbitrationStep.status()))arbitrationMapper.actionState(arbitrationStep.id(),"failed","原渠道退款待处理",at);
          if(!"execution_failed".equals(arbitrationRow.status())){arbitrationMapper.actionState(parent,"failed","原渠道退款待处理",at);arbitrationMapper.updateArbitrationCaseStage(id,"execution_failed",at);}
          return false;
        }
        if(!"success".equals(refund.refundStatus()))return false;
      }else if("arbitration_stock".equals(arbitrationStep.kind())){
        ArbitrationInput.Outcome arbitrationInputOutcome=json.readValue(arbitrationStep.payload(),ArbitrationInput.Outcome.class);
        if(!"no_change".equals(arbitrationInputOutcome.result())){
          ArbitrationMapper.Reservation line=arbitrationMapper.selectArbitrationReservations(arbitrationRow.orderId()).stream().filter(l->l.itemId().equals(arbitrationInputOutcome.itemId()) && Objects.equals(l.snId(),arbitrationInputOutcome.snId())).findFirst().orElseThrow(()->conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name()));
          claimStock.adjudicate(arbitrationRow,line,arbitrationStep.id(),arbitrationStep.actor(),arbitrationInputOutcome.result(),arbitrationInputOutcome.quantity());
        }
      }else throw new IllegalStateException("未知仲裁执行步骤");
      arbitrationMapper.actionState(arbitrationStep.id(),"succeeded",null,at);
      if("execution_failed".equals(arbitrationRow.status())){arbitrationMapper.actionState(parent,"executing",null,at);arbitrationMapper.updateArbitrationCaseStage(id,"executing",at);}
      return true;
    }
    arbitrationMapper.actionState(parent,"succeeded",null,at);arbitrationMapper.updateArbitrationCaseStage(id,"closed",at);
    claimMapper.caseState(arbitrationRow.caseId(),"closed",SystemIdentity.SYSTEM_USER_ID,at);
    if(arbitrationRow.claimId()!=null)claimMapper.updateOrderClaimState(arbitrationRow.claimId(),"executing",null,null,null,null,null,at);
    if(arbitrationRow.issueId()!=null){arbitrationMapper.resolveIssue(arbitrationRow.issueId(),at);arbitrationMapper.closeSourceCase(arbitrationRow.issueId(),SystemIdentity.SYSTEM_USER_ID,at);}
    arbitrationMapper.releaseIntervention(id,arbitrationRow.orderId(),at);
    if(arbitrationRow.issueId()!=null)timers.restore(arbitrationRow.orderId());
    return false;
  }
}
