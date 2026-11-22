package com.mujian.backend.prop.converter;

import com.mujian.backend.prop.domain.PropQrExceptionRecordDO;
import com.mujian.backend.prop.domain.PropSkuAuditDO;
import com.mujian.backend.prop.domain.PropSnAdjustmentAuditDO;
import com.mujian.backend.prop.domain.PropStockAdjustmentAuditDO;
import com.mujian.backend.prop.projection.PropReviewProjection;

public final class PropReviewConverter {
  /** 限制工具类实例化，统一通过静态方法使用。 */
  private PropReviewConverter() {}

  /** 把具体申请记录转换为统一审核字段，不执行数据查询。 */
  public static PropReviewProjection from(PropSkuAuditDO propSkuAuditDO) {
    if (propSkuAuditDO == null) return null;
    PropReviewProjection propReviewProjection = new PropReviewProjection();
    propReviewProjection.setId(propSkuAuditDO.getId());
    propReviewProjection.setSkuId(propSkuAuditDO.getSkuId());
    propReviewProjection.setApplicantUserId(propSkuAuditDO.getApplicantUserId());
    propReviewProjection.setActionType(propSkuAuditDO.getActionType());
    propReviewProjection.setBeforeSkuStatus(propSkuAuditDO.getBeforeSkuStatus());
    propReviewProjection.setTargetSkuStatus(propSkuAuditDO.getTargetSkuStatus());
    propReviewProjection.setBeforeSnapshotJson(propSkuAuditDO.getBeforeSnapshotJson());
    propReviewProjection.setProposedSnapshotJson(propSkuAuditDO.getProposedSnapshotJson());
    propReviewProjection.setInitialSnQuantity(propSkuAuditDO.getInitialSnQuantity());
    propReviewProjection.setInitialStockQuantity(propSkuAuditDO.getInitialStockQuantity());
    propReviewProjection.setAuditStatus(propSkuAuditDO.getAuditStatus());
    propReviewProjection.setPendingSkuGuard(propSkuAuditDO.getPendingSkuGuard());
    propReviewProjection.setApplyRemark(propSkuAuditDO.getApplyRemark());
    propReviewProjection.setAuditRemark(propSkuAuditDO.getAuditRemark());
    propReviewProjection.setRejectReason(propSkuAuditDO.getRejectReason());
    propReviewProjection.setReviewerId(propSkuAuditDO.getReviewerId());
    propReviewProjection.setSubmittedAt(propSkuAuditDO.getSubmittedAt());
    propReviewProjection.setReviewedAt(propSkuAuditDO.getReviewedAt());
    propReviewProjection.setVersion(propSkuAuditDO.getVersion());
    propReviewProjection.setCreatedAt(propSkuAuditDO.getCreatedAt());
    propReviewProjection.setRequestId(propSkuAuditDO.getRequestId());
    propReviewProjection.setRequestHash(propSkuAuditDO.getRequestHash());
    propReviewProjection.setPreviousAuditId(propSkuAuditDO.getPreviousAuditId());
    propReviewProjection.setBaseSkuVersion(propSkuAuditDO.getBaseSkuVersion());
    propReviewProjection.setUpdatedAt(propSkuAuditDO.getUpdatedAt());
    return propReviewProjection;
  }

  /** 把具体申请记录转换为统一审核字段，不执行数据查询。 */
  public static PropReviewProjection from(PropSnAdjustmentAuditDO propSnAdjustmentAuditDO) {
    if (propSnAdjustmentAuditDO == null) return null;
    PropReviewProjection propReviewProjection = new PropReviewProjection();
    propReviewProjection.setId(propSnAdjustmentAuditDO.getId());
    propReviewProjection.setSkuId(propSnAdjustmentAuditDO.getSkuId());
    propReviewProjection.setFactoryId(propSnAdjustmentAuditDO.getFactoryId());
    propReviewProjection.setAdjustmentType(propSnAdjustmentAuditDO.getAdjustmentType());
    propReviewProjection.setIncreaseScene(propSnAdjustmentAuditDO.getIncreaseScene());
    propReviewProjection.setRequestedQuantity(propSnAdjustmentAuditDO.getRequestedQuantity());
    propReviewProjection.setAuditStatus(propSnAdjustmentAuditDO.getAuditStatus());
    propReviewProjection.setApplicantUserId(propSnAdjustmentAuditDO.getApplicantUserId());
    propReviewProjection.setReviewerId(propSnAdjustmentAuditDO.getReviewerId());
    propReviewProjection.setApplyRemark(propSnAdjustmentAuditDO.getApplyRemark());
    propReviewProjection.setAuditRemark(propSnAdjustmentAuditDO.getAuditRemark());
    propReviewProjection.setRejectReason(propSnAdjustmentAuditDO.getRejectReason());
    propReviewProjection.setSubmittedAt(propSnAdjustmentAuditDO.getSubmittedAt());
    propReviewProjection.setReviewedAt(propSnAdjustmentAuditDO.getReviewedAt());
    propReviewProjection.setVersion(propSnAdjustmentAuditDO.getVersion());
    propReviewProjection.setCreatedAt(propSnAdjustmentAuditDO.getCreatedAt());
    propReviewProjection.setRequestId(propSnAdjustmentAuditDO.getRequestId());
    propReviewProjection.setRequestHash(propSnAdjustmentAuditDO.getRequestHash());
    propReviewProjection.setRelatedSkuAuditId(propSnAdjustmentAuditDO.getRelatedSkuAuditId());
    propReviewProjection.setUpdatedAt(propSnAdjustmentAuditDO.getUpdatedAt());
    return propReviewProjection;
  }

  /** 把具体申请记录转换为统一审核字段，不执行数据查询。 */
  public static PropReviewProjection from(PropStockAdjustmentAuditDO propStockAdjustmentAuditDO) {
    if (propStockAdjustmentAuditDO == null) return null;
    PropReviewProjection propReviewProjection = new PropReviewProjection();
    propReviewProjection.setId(propStockAdjustmentAuditDO.getId());
    propReviewProjection.setSkuId(propStockAdjustmentAuditDO.getSkuId());
    propReviewProjection.setAssetType(propStockAdjustmentAuditDO.getAssetType());
    propReviewProjection.setAdjustmentType(propStockAdjustmentAuditDO.getAdjustmentType());
    propReviewProjection.setQuantityDelta(propStockAdjustmentAuditDO.getQuantityDelta());
    propReviewProjection.setAbnormalResult(propStockAdjustmentAuditDO.getAbnormalResult());
    propReviewProjection.setBeforeTotalQuantity(
        propStockAdjustmentAuditDO.getBeforeTotalQuantity());
    propReviewProjection.setBeforeAvailableQuantity(
        propStockAdjustmentAuditDO.getBeforeAvailableQuantity());
    propReviewProjection.setReason(propStockAdjustmentAuditDO.getReason());
    propReviewProjection.setAuditStatus(propStockAdjustmentAuditDO.getAuditStatus());
    propReviewProjection.setApplicantUserId(propStockAdjustmentAuditDO.getApplicantUserId());
    propReviewProjection.setReviewerId(propStockAdjustmentAuditDO.getReviewerId());
    propReviewProjection.setAuditRemark(propStockAdjustmentAuditDO.getAuditRemark());
    propReviewProjection.setRejectReason(propStockAdjustmentAuditDO.getRejectReason());
    propReviewProjection.setRelatedMovementId(propStockAdjustmentAuditDO.getRelatedMovementId());
    propReviewProjection.setRelatedSkuAuditId(propStockAdjustmentAuditDO.getRelatedSkuAuditId());
    propReviewProjection.setSubmittedAt(propStockAdjustmentAuditDO.getSubmittedAt());
    propReviewProjection.setReviewedAt(propStockAdjustmentAuditDO.getReviewedAt());
    propReviewProjection.setVersion(propStockAdjustmentAuditDO.getVersion());
    propReviewProjection.setCreatedAt(propStockAdjustmentAuditDO.getCreatedAt());
    propReviewProjection.setRequestId(propStockAdjustmentAuditDO.getRequestId());
    propReviewProjection.setRequestHash(propStockAdjustmentAuditDO.getRequestHash());
    propReviewProjection.setUpdatedAt(propStockAdjustmentAuditDO.getUpdatedAt());
    return propReviewProjection;
  }

  /** 把具体申请记录转换为统一审核字段，不执行数据查询。 */
  public static PropReviewProjection from(PropQrExceptionRecordDO propQrExceptionRecordDO) {
    if (propQrExceptionRecordDO == null) return null;
    PropReviewProjection propReviewProjection = new PropReviewProjection();
    propReviewProjection.setId(propQrExceptionRecordDO.getId());
    propReviewProjection.setExceptionNo(propQrExceptionRecordDO.getExceptionNo());
    propReviewProjection.setFactoryId(propQrExceptionRecordDO.getFactoryId());
    propReviewProjection.setSkuId(propQrExceptionRecordDO.getSkuId());
    propReviewProjection.setSnId(propQrExceptionRecordDO.getSnId());
    propReviewProjection.setOriginalQrCodeId(propQrExceptionRecordDO.getOriginalQrCodeId());
    propReviewProjection.setReportedByUserId(propQrExceptionRecordDO.getReportedByUserId());
    propReviewProjection.setExceptionType(propQrExceptionRecordDO.getExceptionType());
    propReviewProjection.setDescription(propQrExceptionRecordDO.getDescription());
    propReviewProjection.setEvidenceUrlsJson(propQrExceptionRecordDO.getEvidenceUrlsJson());
    propReviewProjection.setExceptionStatus(propQrExceptionRecordDO.getExceptionStatus());
    propReviewProjection.setActiveOriginalQrGuard(
        propQrExceptionRecordDO.getActiveOriginalQrGuard());
    propReviewProjection.setResolutionType(propQrExceptionRecordDO.getResolutionType());
    propReviewProjection.setReplacementQrCodeId(propQrExceptionRecordDO.getReplacementQrCodeId());
    propReviewProjection.setResolvedByAdminUserId(
        propQrExceptionRecordDO.getResolvedByAdminUserId());
    propReviewProjection.setResolutionReason(propQrExceptionRecordDO.getResolutionReason());
    propReviewProjection.setResolvedAt(propQrExceptionRecordDO.getResolvedAt());
    propReviewProjection.setVersion(propQrExceptionRecordDO.getVersion());
    propReviewProjection.setCreatedAt(propQrExceptionRecordDO.getCreatedAt());
    propReviewProjection.setRequestId(propQrExceptionRecordDO.getRequestId());
    propReviewProjection.setRequestHash(propQrExceptionRecordDO.getRequestHash());
    propReviewProjection.setUpdatedAt(propQrExceptionRecordDO.getUpdatedAt());
    return propReviewProjection;
  }

  /** 将库存历史快照转换为展示字段，不查询数据库。 */
  public static com.mujian.backend.prop.vo.StockSnapshotVO stockSnapshot(
      com.mujian.backend.prop.dto.PropStockSnapshotDTO propStockSnapshotDTO) {
    if (propStockSnapshotDTO == null) return null;
    com.mujian.backend.prop.vo.StockSnapshotVO stockSnapshotVO =
        new com.mujian.backend.prop.vo.StockSnapshotVO()
            .totalQuantity(propStockSnapshotDTO.getTotalQuantity())
            .rentableBaseQuantity(propStockSnapshotDTO.getRentableBaseQuantity())
            .availableQuantity(propStockSnapshotDTO.getAvailableQuantity())
            .lockedQuantity(propStockSnapshotDTO.getLockedQuantity())
            .rentingQuantity(propStockSnapshotDTO.getRentingQuantity())
            .lostQuantity(propStockSnapshotDTO.getLostQuantity())
            .scrappedQuantity(propStockSnapshotDTO.getScrappedQuantity())
            .lastCountedAt(propStockSnapshotDTO.getLastCountedAt())
            .lastCountedBy(propStockSnapshotDTO.getLastCountedBy());
    return stockSnapshotVO;
  }

  /** 将库存台账转换为展示字段，不访问数据库。 */
  public static com.mujian.backend.prop.vo.StockVO stock(
      com.mujian.backend.prop.domain.PropSkuStockDO propSkuStockDO) {

    com.mujian.backend.prop.vo.StockVO stockVO = new com.mujian.backend.prop.vo.StockVO();
    stockVO.setTotalQuantity(propSkuStockDO.getTotalQuantity());
    stockVO.setRentableBaseQuantity(propSkuStockDO.getRentableBaseQuantity());
    stockVO.setAvailableQuantity(propSkuStockDO.getAvailableQuantity());
    stockVO.setLockedQuantity(propSkuStockDO.getLockedQuantity());
    stockVO.setRentingQuantity(propSkuStockDO.getRentingQuantity());
    stockVO.setLostQuantity(propSkuStockDO.getLostQuantity());
    stockVO.setScrappedQuantity(propSkuStockDO.getScrappedQuantity());
    stockVO.setSkuId(propSkuStockDO.getSkuId());
    stockVO.setUpdatedAt(
        com.mujian.backend.common.support.D2Values.time(propSkuStockDO.getUpdatedAt()));
    stockVO.setLastCountedAt(
        com.mujian.backend.common.support.D2Values.time(propSkuStockDO.getLastCountedAt()));
    stockVO.setLastCountedBy(propSkuStockDO.getLastCountedBy());
    return stockVO;
  }
}
