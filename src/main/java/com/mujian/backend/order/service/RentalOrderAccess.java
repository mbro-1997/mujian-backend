package com.mujian.backend.order.service;

import com.mujian.backend.delivery.service.DisputeGuard;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 合法存量订单允许已禁用账号查询及取消，工厂新接单另核对当前状态。 */
@Service
public class RentalOrderAccess {
  private final TradingAccess tradingAccess;
  private final FactoryAccess factoryAccess;
  private final com.mujian.backend.delivery.service.DisputeGuard disputes;
  private final RentalOrderMapper rentalOrderMapper;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public RentalOrderAccess(
      TradingAccess tradingAccess,
      FactoryAccess factoryAccess,
      RentalOrderMapper rentalOrderMapper,com.mujian.backend.delivery.service.DisputeGuard disputes) {
    this.disputes=disputes;
    this.tradingAccess = tradingAccess;
    this.factoryAccess = factoryAccess;
    this.rentalOrderMapper = rentalOrderMapper;
  }

  /** 核对本人或本工厂归属，合法历史不因新增交易限制而丢失。 */
  public RentalOrderDO owned(String actor, String orderId, boolean supplier, boolean write) {
    String factory = null;
    if (supplier) {
      if (!D2Codes.CODE_SUPPLIER.equals(factoryAccess.context(actor, write).roleCode()))
        throw ApiFailure.denied(TradingReason.SUPPLIER_ROLE_REQUIRED.name());
      factory = factoryAccess.profile(actor, write, false).getId();
    } else tradingAccess.buyer(actor);
    RentalOrderDO rentalOrderDO = rentalOrderMapper.selectOrderById(orderId);
    if (rentalOrderDO == null
        || supplier && !rentalOrderDO.factoryId().equals(factory)
        || !supplier && !rentalOrderDO.buyerUserId().equals(actor)) throw missing();
    return rentalOrderDO;
  }

  /** 核对订单状态和未出库条件，禁止越过已履约边界取消。 */
  public void cancellable(RentalOrderDO rentalOrderDO) {
    DisputeGuard.Facts facts=disputes.inspect(rentalOrderDO.id());
    if (!facts.ready() || facts.paused() || facts.terminationActive() || facts.claimActive()) throw conflict(TradingReason.ORDER_STATE_CHANGED.name());
    if (!Set.of(TradingState.pending_accept.name(), TradingState.accepted.name())
            .contains(rentalOrderDO.orderStatus())
        || !TradingState.pending_outbound
            .name()
            .equals(rentalOrderMapper.selectOrderDeliveryStatus(rentalOrderDO.id())))
      throw conflict(TradingReason.ORDER_STATE_CHANGED.name());
  }
}
