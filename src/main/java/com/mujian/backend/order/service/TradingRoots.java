package com.mujian.backend.order.service;

import com.mujian.backend.custom.mapper.CustomOrderMapper;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.factory.mapper.FactoryProfileMapper;
import com.mujian.backend.factory.projection.FactorySourceProjection;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.order.domain.CheckoutBatchDO;
import com.mujian.backend.order.mapper.*;
import com.mujian.backend.order.projection.CheckoutFactoryProjection;
import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.planning.domain.RentalChecklistDO;
import com.mujian.backend.planning.mapper.ChecklistMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 支付、接单、取消和退款共用原来源根锁序，不要求历史交易相关账号仍启用。 */
@Service
public class TradingRoots {
  public record Locked(
      CheckoutBatchDO batch,
      RentalChecklistDO checklist,
      List<String> orderIds,
      PaymentOrderDO payment,
      List<ChannelSuborderDO> suborders) {}

  private final com.mujian.backend.custom.mapper.CustomOrderMapper customOrderMapper;
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final CheckoutMapper checkoutMapper;
  private final FactoryAccess factoryAccess;
  private final FactoryProfileMapper factoryProfileMapper;
  private final ChecklistMapper checklistMapper;
  private final RentalOrderMapper rentalOrderMapper;
  private final PaymentOrderMapper paymentOrderMapper;
  private final com.mujian.backend.order.mapper.ExtensionRequestMapper extensionRequestMapper;

  /** 原父ID只读定位，所有加锁仍从用户根开始。 */
  public TradingRoots(
      CheckoutBatchMapper checkoutBatchMapper,
      CheckoutMapper checkoutMapper,
      FactoryAccess factoryAccess,
      FactoryProfileMapper factoryProfileMapper,
      ChecklistMapper checklistMapper,
      RentalOrderMapper rentalOrderMapper,
      PaymentOrderMapper paymentOrderMapper,com.mujian.backend.order.mapper.ExtensionRequestMapper extensionRequestMapper, com.mujian.backend.custom.mapper.CustomOrderMapper customOrderMapper) {
    this.customOrderMapper=customOrderMapper;
    this.extensionRequestMapper=extensionRequestMapper;
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.checkoutMapper = checkoutMapper;
    this.factoryAccess = factoryAccess;
    this.factoryProfileMapper = factoryProfileMapper;
    this.checklistMapper = checklistMapper;
    this.rentalOrderMapper = rentalOrderMapper;
    this.paymentOrderMapper = paymentOrderMapper;
  }

  /** 按统一锁序锁定付款关联的业务根。 */
  @Transactional(propagation=Propagation.MANDATORY)
  public Locked lockFunding(String key) {
    String custom=customOrderMapper.fundingOrder(key);
    if(custom!=null){
      CustomOrderMapper.Order order=customOrderMapper.selectCustomOrderById(custom,true);
      PaymentOrderDO paymentOrderDO=paymentOrderMapper.selectPaymentById(key,true);
      List<ChannelSuborderDO> subs=paymentOrderMapper.selectSubordersByPayment(key,true);
      if(order==null || paymentOrderDO==null || paymentOrderDO.checkoutBatchId()!=null || !order.buyerId().equals(paymentOrderDO.buyerUserId())
          || subs.size()!=1 || !order.factoryId().equals(subs.get(0).factoryId()) || subs.get(0).rentalOrderId()!=null)
        throw new IllegalStateException("定制支付与原单不符");
      return new Locked(null,null,List.of(custom),paymentOrderDO,subs);
    }
    String original=extensionRequestMapper.fundingBatch(key);
    if (original==null) return lock(key);
    TradingRoots.Locked root=lock(original);
    PaymentOrderDO paymentOrderDOPayment=paymentOrderMapper.selectPaymentById(key,true);
    List<ChannelSuborderDO> subs=paymentOrderMapper.selectSubordersByPayment(key,true);
    if(paymentOrderDOPayment==null || paymentOrderDOPayment.checkoutBatchId()!=null || subs.size()!=1 || !root.orderIds().contains(subs.get(0).rentalOrderId()))
      throw new IllegalStateException("独立支付与原租赁订单不符");
    return new Locked(root.batch(),root.checklist(),root.orderIds(),paymentOrderDOPayment,subs);
  }

  /** 仅在调用者业务事务内取相关根，随后调用者再锁SKU库存及券。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public Locked lock(String batchId) {
    CheckoutBatchDO checkoutBatchDOHint = checkoutBatchMapper.selectBatchById(batchId, false);
    if (checkoutBatchDOHint == null) throw missing();
    List<String> factoryIds = checkoutBatchMapper.selectFactoryIdsByBatch(batchId);
    List<CheckoutFactoryProjection> checkoutFactoryProjectionListFactories =
        checkoutMapper.selectFactoriesByIds(factoryIds);
    if (checkoutFactoryProjectionListFactories.size() != factoryIds.size()
        || checkoutFactoryProjectionListFactories.isEmpty())
      throw new IllegalStateException("trade source missing");
    List<String> users = new ArrayList<>();
    users.add(checkoutBatchDOHint.buyerUserId());
    for (CheckoutFactoryProjection checkoutFactoryProjectionFactory :
        checkoutFactoryProjectionListFactories)
      users.add(checkoutFactoryProjectionFactory.ownerUserId());
    factoryAccess.lock(users.toArray(String[]::new));
    for (CheckoutFactoryProjection checkoutFactoryProjectionFactory :
        checkoutFactoryProjectionListFactories) {
      FactorySourceProjection factorySourceProjectionProfile =
          factoryProfileMapper.selectFactoryProfileByOwner(
              checkoutFactoryProjectionFactory.ownerUserId(), true);
      if (factorySourceProjectionProfile == null
          || !checkoutFactoryProjectionFactory.id().equals(factorySourceProjectionProfile.getId()))
        throw new IllegalStateException("trade factory source changed");
    }
    RentalChecklistDO rentalChecklistDOChecklist =
        checklistMapper.selectOwnedChecklistIncludingDeletedById(
            checkoutBatchDOHint.buyerUserId(), checkoutBatchDOHint.sourceChecklistId(), true);
    CheckoutBatchDO checkoutBatchDOBatch = checkoutBatchMapper.selectBatchById(batchId, true);
    if (rentalChecklistDOChecklist == null
        || !checkoutBatchDOBatch.sourceChecklistId().equals(rentalChecklistDOChecklist.id()))
      throw new IllegalStateException("trade checklist source missing");
    List<String> orders = rentalOrderMapper.selectOrderIdsByBatch(batchId, true);
    PaymentOrderDO paymentOrderDO = paymentOrderMapper.selectPaymentByBatch(batchId, true);
    if (paymentOrderDO == null) throw new IllegalStateException("trade payment source missing");
    List<ChannelSuborderDO> channelSuborderDOListSuborders =
        paymentOrderMapper.selectSubordersByPayment(paymentOrderDO.id(), true);
    if (channelSuborderDOListSuborders.size() != factoryIds.size())
      throw new IllegalStateException("trade suborders incomplete");
    return new Locked(
        checkoutBatchDOBatch,
        rentalChecklistDOChecklist,
        List.copyOf(orders),
        paymentOrderDO,
        List.copyOf(channelSuborderDOListSuborders));
  }
}
