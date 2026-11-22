package com.mujian.backend.prop.converter;

import static com.mujian.backend.common.support.D2Values.time;

import com.mujian.backend.common.enums.PropAssetType;
import com.mujian.backend.prop.domain.*;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.projection.PropInventorySummaryProjection;
import com.mujian.backend.prop.service.PropQrLookup.Chain;
import com.mujian.backend.prop.vo.*;

/** 工厂与管理目录共用的纯字段组装，不读取数据库或外部资源。 */
public final class OwnedSkuConverter {
  /** 仅通过静态方法组装响应。 */
  private OwnedSkuConverter() {}

  /** 从已读取的资料、库存、申请及权限标志组装一件道具。 */
  public static OwnedSkuVO convert(
      PropSkuDO propSkuDO,
      PropInventorySummaryProjection propInventorySummaryProjectionStats,
      PropSkuAuditDO propSkuAuditDO,
      Chain chain,
      SkuReadVO skuReadVO,
      boolean writable) {
    boolean a = propSkuDO.getAssetType().equals(PropAssetType.A_ASSET.name());
    if (propInventorySummaryProjectionStats == null) throw PropAccess.inconsistent();
    boolean formed = propInventorySummaryProjectionStats.getFormed() != 0;
    if (formed && !a && propInventorySummaryProjectionStats.getStockId() == null)
      throw PropAccess.inconsistent();
    InventorySummaryVO inventorySummaryVO =
        formed
            ? new InventorySummaryVO()
                .totalQuantity(
                    (a
                        ? propInventorySummaryProjectionStats.getSnTotal()
                        : propInventorySummaryProjectionStats.getTotalQuantity()))
                .availableQuantity(
                    (a
                        ? propInventorySummaryProjectionStats.getSnAvailable()
                        : propInventorySummaryProjectionStats.getAvailableQuantity()))
            : null;
    return new OwnedSkuVO()
        .id(propSkuDO.getId())
        .skuCode(propSkuDO.getSkuCode())
        .factoryId(propSkuDO.getFactoryId())
        .factoryName(propInventorySummaryProjectionStats.getFactoryName())
        .assetType(propSkuDO.getAssetType())
        .skuStatus(propSkuDO.getSkuStatus())
        .fillStatus(propSkuDO.getFillStatus())
        .version(propSkuDO.getVersion())
        .fields(skuReadVO)
        .currentQrId(chain == null || chain.current() == null ? null : chain.current().getId())
        .latestQrId(chain == null ? null : chain.latest().getId())
        .inventorySummary(inventorySummaryVO)
        .canAdjustInventory(writable && formed)
        .pendingApplicationId(propSkuAuditDO == null ? null : propSkuAuditDO.getId())
        .canEditOptions(writable)
        .canApplyUp(writable && propSkuDO.getSkuStatus().equals("offline"))
        .canApplyDown(writable && propSkuDO.getSkuStatus().equals("online"))
        .createdAt(time(propSkuDO.getCreatedAt()))
        .updatedAt(time(propSkuDO.getUpdatedAt()));
  }
}
