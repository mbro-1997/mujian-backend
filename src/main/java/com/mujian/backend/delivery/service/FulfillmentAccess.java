package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.RentalOrderAccess;
import org.springframework.stereotype.Service;

/** 存量履约使用可信订单归属；禁新交易状态不没收本人收尾权利。 */
@Service
public class FulfillmentAccess {
  private final RentalOrderAccess rentalOrderAccess;
  private final RentalOrderMapper rentalOrderMapper;
  private final VerificationAccess verificationAccess;

  /** 复用既有工厂归属和管理权限。 */
  public FulfillmentAccess(
      RentalOrderAccess rentalOrderAccess,
      RentalOrderMapper rentalOrderMapper,
      VerificationAccess verificationAccess) {
    this.rentalOrderAccess = rentalOrderAccess;
    this.rentalOrderMapper = rentalOrderMapper;
    this.verificationAccess = verificationAccess;
  }

  /** 按现有管理员权限决定是否展示提前结束动作。 */
  public boolean mayCloseReturn(String actor) {
    return verificationAccess.has(actor, "admin:fulfillment:return:close");
  }

  /** 先认证归属再读取子根或原回执；跨工厂始终按不存在处理。 */
  public RentalOrderDO order(String actor, String orderId, boolean supplier, String permission) {
    if (permission == null) return rentalOrderAccess.owned(actor, id(orderId), supplier, false);
    verificationAccess.admin(actor, permission);
    RentalOrderDO rentalOrderDO = rentalOrderMapper.selectOrderById(id(orderId));
    if (rentalOrderDO == null) throw missing();
    return rentalOrderDO;
  }
}
