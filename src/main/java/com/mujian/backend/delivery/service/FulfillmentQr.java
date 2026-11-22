package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.delivery.mapper.ReturnCheckMapper;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.prop.domain.*;
import com.mujian.backend.prop.mapper.PropQrCodeMapper;
import com.mujian.backend.prop.policy.PropQrInput;
import com.mujian.backend.prop.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 去回程均按原分配和当前码验实物，回程另须真实出库记录。 */
@Service
public class FulfillmentQr {
  public record Assigned(String itemId, String qrId, String snId) {}

  private final TradingReservations tradingReservations;
  private final PropQrCodeMapper propQrCodeMapper;
  private final PropQrLookup propQrLookup;
  private final ReturnCheckMapper returnCheckMapper;

  /** 复用原库存锁和二维码替换链。 */
  public FulfillmentQr(
      TradingReservations tradingReservations,
      PropQrCodeMapper propQrCodeMapper,
      PropQrLookup propQrLookup,
      ReturnCheckMapper returnCheckMapper) {
    this.tradingReservations = tradingReservations;
    this.propQrCodeMapper = propQrCodeMapper;
    this.propQrLookup = propQrLookup;
    this.returnCheckMapper = returnCheckMapper;
  }

  /** 仅从已分配库存定位本单明细，扫码文本不能指定订单或实物主键。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public Assigned resolve(RentalOrderDO order, String raw, boolean returning) {
    String code;
    try {
      code = PropQrInput.parse(raw);
    } catch (IllegalArgumentException e) {
      throw conflict(FulfillmentReason.QR_INVALID.name());
    }
    List<PropQrCodeDO> matches = propQrCodeMapper.selectPropQrCodeListByCode(code);
    if (matches.size() != 1) throw conflict(FulfillmentReason.QR_INVALID.name());
    PropQrCodeDO propQrCodeDO = matches.get(0);
    Set<String> statuses = returning ? Set.of("renting", "overdue_holding") : Set.of("paid_locked");
    for (InventoryReservationDO inventoryReservationDO : tradingReservations.lockExisting(order.checkoutBatchId())) {
      if (!order.id().equals(inventoryReservationDO.getRentalOrderId())
          || !propQrCodeDO.getTargetId().equals(inventoryReservationDO.getSnId())
          || !statuses.contains(inventoryReservationDO.getReservationStatus())) continue;
      if (!PropQrTargetType.SN.name().equals(propQrCodeDO.getTargetType())
          || !propQrCodeDO.getSkuId().equals(inventoryReservationDO.getSkuId())) break;
      PropQrLookup.Chain chain =
          propQrLookup.find(PropQrTargetType.SN.name(), List.of(inventoryReservationDO.getSnId())).get(inventoryReservationDO.getSnId());
      if (chain == null || chain.current() == null || !chain.current().getId().equals(propQrCodeDO.getId()))
        throw conflict(FulfillmentReason.QR_NOT_ACTIVE.name());
      if (returning
          && !inventoryReservationDO.getRentalOrderItemId()
              .equals(returnCheckMapper.selectDispatchedItem(order.id(), inventoryReservationDO.getSnId()))) break;
      return new Assigned(inventoryReservationDO.getRentalOrderItemId(), propQrCodeDO.getId(), inventoryReservationDO.getSnId());
    }
    throw conflict(FulfillmentReason.QR_NOT_IN_ORDER.name());
  }
}
