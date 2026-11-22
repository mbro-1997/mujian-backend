package com.mujian.backend.order.service;

import com.mujian.backend.payment.domain.PaymentOrderDO;

import static com.mujian.backend.common.support.D2Values.*;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.ExtensionRequestMapper;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.payment.service.PaymentExecution;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ExtensionPayment {
  public record Launch(String resourceId,BatchViews.Payment payment,PaymentGateway.Parameters parameters,String nextAction) {}
  private final OrderExtensions extensions;
  private final ExtensionRequestMapper extensionRequestMapper;
  private final PaymentOrderMapper paymentOrderMapper;
  private final PaymentExecution execution;
  private final PaymentGateway paymentGateway;
  private final TradingOperations tradingOperations;
  private final Clock clock;
  private final com.mujian.backend.factory.service.FactoryAccess factoryAccess;

  public ExtensionPayment(OrderExtensions extensions,ExtensionRequestMapper extensionRequestMapper,PaymentOrderMapper paymentOrderMapper,PaymentExecution execution,
      PaymentGateway paymentGateway,TradingOperations tradingOperations,Clock clock,com.mujian.backend.factory.service.FactoryAccess factoryAccess) {
    this.factoryAccess=factoryAccess;this.extensions=extensions;this.extensionRequestMapper=extensionRequestMapper;this.paymentOrderMapper=paymentOrderMapper;this.execution=execution;this.paymentGateway=paymentGateway;this.tradingOperations=tradingOperations;this.clock=clock;
  }
  /** 使用原支付记录准备渠道付款参数。 */
  public Launch launch(TradingCommand tradingCommand,String id,OrderExtensions.Days days) {
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);
    if(tradingReceipt==null) {
      paymentGateway.requireReady();
      tradingReceipt=tradingCommand.operation()==TradingOperation.submitExtension?extensions.submit(tradingCommand,id,days):extensions.command(tradingCommand,id,days.expectedVersion(),null);
    }
    ExtensionRequestMapper.Row ext=extensionRequestMapper.selectExtensionById(tradingReceipt.resourceId());
    if(ext==null || !tradingCommand.actor().equals(ext.buyerId()))throw missing();
    if("pending_payment".equals(ext.status())){factoryAccess.context(tradingCommand.actor(),true);execution.execute(ext.paymentId());}
    return parameters(ext.id(),ext.paymentId());
  }
  /** 按渠道应答构造本次付款的调起参数。 */
  public Launch parameters(String resource,String payment) {
    return parameters(resource,payment,true);
  }
  /** 按渠道应答构造本次付款的调起参数。 */
  public Launch parameters(String resource,String payment,boolean allowParameters) {
    PaymentOrderDO paymentOrderDO=paymentOrderMapper.selectPaymentById(payment,false);
    boolean pending=Set.of("pending","paying").contains(paymentOrderDO.paymentStatus());
    PaymentGateway.Parameters parameters=allowParameters && pending && paymentOrderDO.channelPrepayId()!=null && paymentOrderDO.closeRequestedAt()==null && now(clock).isBefore(paymentOrderDO.expireAt())?
        paymentGateway.parameters(paymentOrderDO.channelAppId(),paymentOrderDO.channelPrepayId()):null;
    return new Launch(resource,new BatchViews.Payment(paymentOrderDO.id(),paymentOrderDO.paymentNo(),paymentOrderDO.paymentStatus(),paymentOrderDO.payableAmountFen(),paymentOrderDO.paidAmountFen(),
        paymentOrderDO.refundedAmountFen(),time(paymentOrderDO.expireAt()),time(paymentOrderDO.paidAt()),time(paymentOrderDO.lastCheckedAt())),parameters,parameters!=null?"pay":pending?"wait":"view_result");
  }
}
