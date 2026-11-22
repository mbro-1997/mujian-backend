package com.mujian.backend.order.projection;

import com.mujian.backend.address.domain.ShippingAddressDO;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.planning.domain.*;
import com.mujian.backend.prop.domain.PropSkuDO;
import java.util.List;
import java.util.Map;

/** 一次数据库一致读取供事务外路线与金额计算，不持有写锁等待网络。 */
public record CheckoutSource(
    RentalChecklistDO checklist,
    ShippingAddressDO address,
    List<RentalChecklistItemDO> items,
    Map<String, PropSkuDO> skus,
    Map<String, SnapshotV1> snapshots,
    List<CheckoutFactoryProjection> factories,
    List<VehicleDO> vehicles,
    List<PriceRuleDO> rules) {}
