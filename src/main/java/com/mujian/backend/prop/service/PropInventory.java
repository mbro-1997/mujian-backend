package com.mujian.backend.prop.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.infrastructure.persistence.MappingInputs.first;

import com.mujian.backend.common.enums.PropAssetType;
import com.mujian.backend.common.enums.PropQrTargetType;
import com.mujian.backend.common.enums.PropReasonCode;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.prop.converter.PropReviewConverter;
import com.mujian.backend.prop.domain.PropInstanceStatusLogDO;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.domain.PropSkuAuditDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSkuStockDO;
import com.mujian.backend.prop.domain.PropSnAdjustmentAuditDO;
import com.mujian.backend.prop.domain.PropSnAdjustmentItemDO;
import com.mujian.backend.prop.domain.PropSnInstanceDO;
import com.mujian.backend.prop.domain.PropStockAdjustmentAuditDO;
import com.mujian.backend.prop.domain.PropStockMovementDO;
import com.mujian.backend.prop.dto.PropStockSnapshotDTO;
import com.mujian.backend.prop.mapper.PropInstanceStatusLogMapper;
import com.mujian.backend.prop.mapper.PropQrCodeMapper;
import com.mujian.backend.prop.mapper.PropSkuStockMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentAuditMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentItemMapper;
import com.mujian.backend.prop.mapper.PropSnInstanceMapper;
import com.mujian.backend.prop.mapper.PropStockAdjustmentAuditMapper;
import com.mujian.backend.prop.mapper.PropStockMovementMapper;
import com.mujian.backend.prop.projection.PropReviewProjection;
import com.mujian.backend.prop.utils.PropJson;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Inventory effects run only inside the caller's locked business transaction. */
@Service
public class PropInventory {
  public static final List<String> COUNTS =
      List.of(
          "total_quantity",
          "rentable_base_quantity",
          "available_quantity",
          "locked_quantity",
          "renting_quantity",
          "lost_quantity",
          "scrapped_quantity");
  private final PropSkuStockMapper propSkuStockMapper;
  private final PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper;
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final PropQrCodeMapper propQrCodeMapper;
  private final PropStockMovementMapper propStockMovementMapper;
  private final PropInstanceStatusLogMapper propInstanceStatusLogMapper;
  private final PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper;
  private final PropSnAdjustmentItemMapper propSnAdjustmentItemMapper;
  private final com.mujian.backend.prop.utils.PropBatchWriter propBatchWriter;
  private final PropJson propJson;
  private final Clock clock;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropInventory(
      PropSkuStockMapper propSkuStockMapper,
      PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper,
      PropSnInstanceMapper propSnInstanceMapper,
      PropQrCodeMapper propQrCodeMapper,
      PropStockMovementMapper propStockMovementMapper,
      PropInstanceStatusLogMapper propInstanceStatusLogMapper,
      PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper,
      PropSnAdjustmentItemMapper propSnAdjustmentItemMapper,
      PropJson propJson,
      Clock clock,
      com.mujian.backend.prop.utils.PropBatchWriter propBatchWriter) {
    this.propBatchWriter = propBatchWriter;
    this.propSkuStockMapper = propSkuStockMapper;
    this.propStockAdjustmentAuditMapper = propStockAdjustmentAuditMapper;
    this.propSnInstanceMapper = propSnInstanceMapper;
    this.propQrCodeMapper = propQrCodeMapper;
    this.propStockMovementMapper = propStockMovementMapper;
    this.propInstanceStatusLogMapper = propInstanceStatusLogMapper;
    this.propSnAdjustmentAuditMapper = propSnAdjustmentAuditMapper;
    this.propSnAdjustmentItemMapper = propSnAdjustmentItemMapper;
    this.propJson = propJson;
    this.clock = clock;
  }

  /** 读取道具的库存台账并校验数据唯一性。 */
  public PropSkuStockDO stock(String sku, boolean lock) {
    PropSkuStockDO propSkuStockDO =
        first(propSkuStockMapper.selectPropSkuStockListBySkuId(sku, lock));
    if (propSkuStockDO == null) throw missing();
    com.mujian.backend.prop.projection.CurrentStockQuantityProjection
        currentStockQuantityProjection =
            propSkuStockMapper.selectCurrentStockQuantities(sku, now(clock).toLocalDate());
    propSkuStockDO.setAvailableQuantity(currentStockQuantityProjection.available().intValueExact());
    propSkuStockDO.setLockedQuantity(currentStockQuantityProjection.locked().intValueExact());
    propSkuStockDO.setRentingQuantity(currentStockQuantityProjection.renting().intValueExact());
    return propSkuStockDO;
  }

  /** 读取指定实物资产并按需加锁。 */
  public PropSnInstanceDO sn(String id, boolean lock) {
    PropSnInstanceDO propSnInstanceDO = propSnInstanceMapper.selectPropSnInstance(id(id), lock);
    if (propSnInstanceDO == null) throw missing();
    return propSnInstanceDO;
  }

  /** 读取当前业务对象尚未处理的申请，阻止冲突操作。 */
  public PropSnAdjustmentAuditDO pending(String sn) {
    return first(propSnAdjustmentAuditMapper.selectPendingSnAdjustmentListBySnId(sn));
  }

  /** 按固定顺序锁定审核涉及的实物资产。 */
  public List<PropSnAdjustmentItemDO> lockItems(String source) {
    List<PropSnAdjustmentItemDO> propSnAdjustmentItemDOList =
        propSnAdjustmentItemMapper.selectSnAdjustmentItemListByAuditId(source);
    for (PropSnAdjustmentItemDO propSnAdjustmentItemDO : propSnAdjustmentItemDOList)
      sn(propSnAdjustmentItemDO.getSnId(), true);
    return propSnAdjustmentItemDOList;
  }

  /** 创建绑定业务目标的二维码记录。 */
  public PropQrCodeDO createQrRecord(
      String sku, String type, String target, String admin, String previous) {
    String rid = Ids.next(), code = Ids.next();
    propQrCodeMapper.insertPropQrCode(
        new PropQrCodeDO()
            .id(rid)
            .qrCodeId(code)
            .codeType("wechat_mini_code")
            .targetType(type)
            .targetId(target)
            .skuId(sku)
            .supersedesQrCodeId(previous)
            .qrScene(code)
            .qrPage("pages/props/scan/index")
            .status("bound")
            .createdByAdminUserId(admin)
            .version(0)
            .imageGenerationStatus(CODE_PENDING)
            .imageAttempts(0)
            .createdAt(now(clock))
            .updatedAt(now(clock)));
    return propQrCodeMapper.selectPropQrCode(rid, false);
  }

  /** 记录资产状态变更及对应业务来源。 */
  public void log(
      PropSnInstanceDO propSnInstanceDO,
      String from,
      String to,
      String source,
      String reason,
      String actor,
      String role,
      String adjustment,
      String business) {
    propInstanceStatusLogMapper.insertPropInstanceStatusLog(
        new PropInstanceStatusLogDO()
            .id(Ids.next())
            .propInstanceId(propSnInstanceDO.getId())
            .skuId(propSnInstanceDO.getSkuId())
            .snCode(propSnInstanceDO.getSnCode())
            .qrCodeId(propSnInstanceDO.getQrCodeId())
            .fromStatus(from)
            .toStatus(to)
            .sourceType(source)
            .reason(reason)
            .operatorUserId(actor)
            .operatorRole(role)
            .relatedAdjustmentAuditId(adjustment)
            .businessKey(business)
            .createdAt(now(clock)));
  }

  /** 更新实物状态并保留版本、来源和操作留痕。 */
  public void transition(
      PropSnInstanceDO propSnInstanceDO,
      String target,
      String actor,
      String role,
      String source,
      String reason,
      String adjustment,
      String key) {
    propSnInstanceMapper.updatePropSnStatusByVersion(
        new com.mujian.backend.prop.dto.PropWriteDTO.TransitionSn(
            propSnInstanceDO.getId(),
            target,
            reason,
            now(clock),
            actor,
            Math.addExact(propSnInstanceDO.getVersion(), 1),
            now(clock)));
    log(
        propSnInstanceDO,
        propSnInstanceDO.getInstanceStatus(),
        target,
        source,
        reason,
        actor,
        role,
        adjustment,
        key);
  }

  /** 按既定批次顺序创建实物、二维码和状态流水。 */
  public void increaseSn(PropReviewProjection propReviewProjection, String admin) {
    // Chunk size bounds memory and JDBC packets; it is not a product quantity limit.
    for (int offset = 0; offset < propReviewProjection.getRequestedQuantity(); ) {
      int count = Math.min(500, propReviewProjection.getRequestedQuantity() - offset);
      ArrayList<PropQrCodeDO> propQrCodeDOList = new ArrayList<PropQrCodeDO>();
      ArrayList<PropSnInstanceDO> propSnInstanceDOList = new ArrayList<PropSnInstanceDO>();
      ArrayList<PropInstanceStatusLogDO> propInstanceStatusLogDOList =
          new ArrayList<PropInstanceStatusLogDO>();
      LocalDateTime at = now(clock);
      for (int i = 0; i < count; i++) {
        String sid = Ids.next(), rid = Ids.next(), code = Ids.next();
        propQrCodeDOList.add(
            new PropQrCodeDO()
                .id(rid)
                .qrCodeId(code)
                .codeType("wechat_mini_code")
                .targetType(PropQrTargetType.SN.name())
                .targetId(sid)
                .skuId(propReviewProjection.getSkuId())
                .qrScene(code)
                .qrPage("pages/props/scan/index")
                .status("bound")
                .createdByAdminUserId(admin)
                .version(0)
                .imageGenerationStatus(CODE_PENDING)
                .imageAttempts(0)
                .createdAt(at)
                .updatedAt(at));
        propSnInstanceDOList.add(
            new PropSnInstanceDO()
                .id(sid)
                .skuId(propReviewProjection.getSkuId())
                .sourceAdjustmentAuditId(propReviewProjection.getId())
                .snCode("SN-" + sid)
                .qrCodeId(code)
                .instanceStatus("pending_label")
                .statusChangedAt(at)
                .statusChangedBy(admin)
                .version(0)
                .createdAt(at)
                .updatedAt(at));
        propInstanceStatusLogDOList.add(
            new PropInstanceStatusLogDO()
                .id(Ids.next())
                .propInstanceId(sid)
                .skuId(propReviewProjection.getSkuId())
                .snCode("SN-" + sid)
                .qrCodeId(code)
                .fromStatus(null)
                .toStatus("pending_label")
                .sourceType("manual_adjustment")
                .reason(propReviewProjection.getApplyRemark())
                .operatorUserId(admin)
                .operatorRole(FulfillmentRole.admin.name())
                .relatedAdjustmentAuditId(propReviewProjection.getId())
                .businessKey("sn-adjust:" + propReviewProjection.getId() + ":" + sid)
                .createdAt(at));
      }
      propBatchWriter.insertQrCodes(propQrCodeDOList);
      propBatchWriter.insertInstances(propSnInstanceDOList);
      propBatchWriter.insertStatusLogs(propInstanceStatusLogDOList);
      offset += count;
    }
  }

  /** 将库存历史快照转换为展示字段，不查询数据库。 */
  public com.mujian.backend.prop.dto.PropStockSnapshotDTO stockSnapshot(
      PropSkuStockDO propSkuStockDO) {
    PropStockSnapshotDTO propStockSnapshotDTO =
        new com.mujian.backend.prop.dto.PropStockSnapshotDTO();
    propStockSnapshotDTO.setSchemaVersion(1);
    propStockSnapshotDTO.setTotalQuantity(
        propSkuStockDO == null ? 0 : propSkuStockDO.getTotalQuantity());
    propStockSnapshotDTO.setRentableBaseQuantity(
        propSkuStockDO == null ? 0 : propSkuStockDO.getRentableBaseQuantity());
    propStockSnapshotDTO.setAvailableQuantity(
        propSkuStockDO == null ? 0 : propSkuStockDO.getAvailableQuantity());
    propStockSnapshotDTO.setLockedQuantity(
        propSkuStockDO == null ? 0 : propSkuStockDO.getLockedQuantity());
    propStockSnapshotDTO.setRentingQuantity(
        propSkuStockDO == null ? 0 : propSkuStockDO.getRentingQuantity());
    propStockSnapshotDTO.setLostQuantity(
        propSkuStockDO == null ? 0 : propSkuStockDO.getLostQuantity());
    propStockSnapshotDTO.setScrappedQuantity(
        propSkuStockDO == null ? 0 : propSkuStockDO.getScrappedQuantity());
    propStockSnapshotDTO.setLastCountedAt(
        propSkuStockDO == null ? null : time(propSkuStockDO.getLastCountedAt()));
    propStockSnapshotDTO.setLastCountedBy(
        propSkuStockDO == null ? null : propSkuStockDO.getLastCountedBy());
    return propStockSnapshotDTO;
  }

  /** 校验库存增减后的整数边界，拒绝溢出或负数。 */
  private int changedQuantity(int before, int delta) {
    long result = (long) before + delta;
    if (result < 0 || result > Integer.MAX_VALUE)
      throw conflict(PropReasonCode.STOCK_NOT_ENOUGH.name());
    return (int) result;
  }

  /** 在同一库存根锁内核对减库后能覆盖全部未来有效占用峰值。 */
  public void assertReduction(PropSkuStockDO propSkuStockDO, int delta) {
    if (delta >= 0) return;
    long reduction = -(long) delta;
    if (propSkuStockDO.getAvailableQuantity() < reduction)
      throw conflict(PropReasonCode.STOCK_NOT_ENOUGH.name());
    List<com.mujian.backend.prop.projection.PropQuantityProjection> quantities =
        propSkuStockMapper.selectStockOccupancyListBySkuIdsAndDates(
            List.of(propSkuStockDO.getSkuId()),
            now(clock).toLocalDate(),
            LocalDate.of(9999, 12, 31),
            now(clock).toLocalDate());
    if (quantities.size() != 1 || quantities.get(0).getAmount().longValueExact() < reduction)
      throw conflict(PropReasonCode.STOCK_NOT_ENOUGH.name());
  }

  /** 按已批准数量变更库存台账并登记库存流水。 */
  public void changeStock(
      PropReviewProjection propReviewProjection, String admin, boolean initial) {
    PropSkuStockDO propSkuStockDO = initial ? null : stock(propReviewProjection.getSkuId(), true);
    int delta = propReviewProjection.getQuantityDelta();
    if (!initial) assertReduction(propSkuStockDO, delta);
    PropStockSnapshotDTO propStockSnapshotDTO = stockSnapshot(propSkuStockDO);
    propStockSnapshotDTO.setTotalQuantity(
        changedQuantity(propStockSnapshotDTO.getTotalQuantity(), delta));
    propStockSnapshotDTO.setRentableBaseQuantity(
        changedQuantity(propStockSnapshotDTO.getRentableBaseQuantity(), delta));
    propStockSnapshotDTO.setAvailableQuantity(
        changedQuantity(propStockSnapshotDTO.getAvailableQuantity(), delta));
    if ("lost".equals(propReviewProjection.getAbnormalResult()))
      propStockSnapshotDTO.setLostQuantity(
          changedQuantity(propStockSnapshotDTO.getLostQuantity(), -delta));
    if ("scrapped".equals(propReviewProjection.getAbnormalResult()))
      propStockSnapshotDTO.setScrappedQuantity(
          changedQuantity(propStockSnapshotDTO.getScrappedQuantity(), -delta));
    propStockSnapshotDTO.setLastCountedAt(time(now(clock)));
    propStockSnapshotDTO.setLastCountedBy(admin);
    PropSkuStockDO propSkuStockDOUpdated =
        new PropSkuStockDO()
            .totalQuantity(propStockSnapshotDTO.getTotalQuantity())
            .rentableBaseQuantity(propStockSnapshotDTO.getRentableBaseQuantity())
            .availableQuantity(propStockSnapshotDTO.getAvailableQuantity())
            .lockedQuantity(propStockSnapshotDTO.getLockedQuantity())
            .rentingQuantity(propStockSnapshotDTO.getRentingQuantity())
            .lostQuantity(propStockSnapshotDTO.getLostQuantity())
            .scrappedQuantity(propStockSnapshotDTO.getScrappedQuantity())
            .lastCountedAt(now(clock))
            .lastCountedBy(admin)
            .updatedAt(now(clock));
    if (initial)
      propSkuStockMapper.insertPropSkuStock(
          propSkuStockDOUpdated
              .id(Ids.next())
              .skuId(propReviewProjection.getSkuId())
              .createdAt(now(clock)));
    else
      propSkuStockMapper.updatePropSkuStockByVersion(
          propSkuStockDOUpdated.id(propSkuStockDO.getId()));
    String mid = Ids.next();
    propStockMovementMapper.insertPropStockMovement(
        new PropStockMovementDO()
            .id(mid)
            .skuId(propReviewProjection.getSkuId())
            .movementType(propReviewProjection.getAdjustmentType())
            .sourceType("manual_adjustment")
            .quantity(delta)
            .beforeQuantity(propSkuStockDO == null ? 0 : propSkuStockDO.getTotalQuantity())
            .afterQuantity(propStockSnapshotDTO.getTotalQuantity())
            .beforeStockSnapshotJson(propJson.write(stockSnapshot(propSkuStockDO)))
            .afterStockSnapshotJson(propJson.write(propStockSnapshotDTO))
            .relatedAuditId(propReviewProjection.getId())
            .operatorUserId(admin)
            .operatorRole(FulfillmentRole.admin.name())
            .remark(propReviewProjection.getReason())
            .businessKey("stock-adjust:" + propReviewProjection.getId())
            .createdAt(now(clock)));
    propStockAdjustmentAuditMapper.updateStockAdjustmentMovement(
        new com.mujian.backend.prop.dto.PropWriteDTO.LinkStockMovement(
            propReviewProjection.getId(), mid));
  }

  /** 根据首次上架的资产类型建立实物或库存台账。 */
  public void initial(PropSkuDO propSkuDO, PropSkuAuditDO propSkuAuditDO, String admin) {
    String sid = Ids.next();
    boolean a = PropAssetType.A_ASSET.name().equals(propSkuDO.getAssetType());
    if (a) {
      propSnAdjustmentAuditMapper.insertPropSnAdjustmentAudit(
          new PropSnAdjustmentAuditDO()
              .id(sid)
              .skuId(propSkuDO.getId())
              .adjustmentType("increase")
              .auditStatus(CODE_APPROVED)
              .applicantUserId(propSkuAuditDO.getApplicantUserId())
              .reviewerId(admin)
              .auditRemark(propSkuAuditDO.getAuditRemark())
              .submittedAt(propSkuAuditDO.getSubmittedAt())
              .reviewedAt(now(clock))
              .version(1)
              .createdAt(now(clock))
              .updatedAt(now(clock))
              .relatedSkuAuditId(propSkuAuditDO.getId())
              .factoryId(propSkuDO.getFactoryId())
              .increaseScene("initial")
              .requestedQuantity(propSkuAuditDO.getInitialSnQuantity())
              .applyRemark("首次上架入库"));
      increaseSn(
          PropReviewConverter.from(
              propSnAdjustmentAuditMapper.selectPropSnAdjustmentAudit(sid, false)),
          admin);
    } else {
      propStockAdjustmentAuditMapper.insertPropStockAdjustmentAudit(
          new PropStockAdjustmentAuditDO()
              .id(sid)
              .skuId(propSkuDO.getId())
              .adjustmentType("increase")
              .auditStatus(CODE_APPROVED)
              .applicantUserId(propSkuAuditDO.getApplicantUserId())
              .reviewerId(admin)
              .auditRemark(propSkuAuditDO.getAuditRemark())
              .submittedAt(propSkuAuditDO.getSubmittedAt())
              .reviewedAt(now(clock))
              .version(1)
              .createdAt(now(clock))
              .updatedAt(now(clock))
              .relatedSkuAuditId(propSkuAuditDO.getId())
              .assetType(PropAssetType.B_CONSUMABLE.name())
              .quantityDelta(propSkuAuditDO.getInitialStockQuantity())
              .beforeTotalQuantity(0)
              .beforeAvailableQuantity(0)
              .reason("首次上架入库"));
      changeStock(
          PropReviewConverter.from(
              propStockAdjustmentAuditMapper.selectPropStockAdjustmentAudit(sid, false)),
          admin,
          true);
      createQrRecord(
          propSkuDO.getId(), PropQrTargetType.SKU.name(), propSkuDO.getId(), admin, null);
    }
  }
}
