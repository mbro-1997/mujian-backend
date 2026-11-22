package com.mujian.backend.prop.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.PropReviewSources.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.enums.PropAssetType;
import com.mujian.backend.prop.converter.PropReviewConverter;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSnAdjustmentItemDO;
import com.mujian.backend.prop.domain.PropSnInstanceDO;
import com.mujian.backend.prop.dto.QrExceptionQueryDTO;
import com.mujian.backend.prop.dto.ReviewQueryDTO;
import com.mujian.backend.prop.mapper.PropQrCodeMapper;
import com.mujian.backend.prop.mapper.PropQrExceptionRecordMapper;
import com.mujian.backend.prop.mapper.PropReviewMapper;
import com.mujian.backend.prop.mapper.PropSkuMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentItemMapper;
import com.mujian.backend.prop.mapper.PropSnInstanceMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.projection.PropApplicantProjection;
import com.mujian.backend.prop.projection.PropFactoryProjection;
import com.mujian.backend.prop.projection.PropQrExceptionProjection;
import com.mujian.backend.prop.projection.PropQrExceptionStatsProjection;
import com.mujian.backend.prop.projection.PropReviewCountProjection;
import com.mujian.backend.prop.projection.PropReviewListProjection;
import com.mujian.backend.prop.projection.PropReviewProjection;
import com.mujian.backend.prop.service.PropCasework;
import com.mujian.backend.prop.service.PropInventory;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.service.PropQueryService;
import com.mujian.backend.prop.service.PropReviewQueryService;
import com.mujian.backend.prop.service.PropReviewService;
import com.mujian.backend.prop.service.PropSnapshots;
import com.mujian.backend.prop.utils.PropJson;
import com.mujian.backend.prop.vo.AdjustmentItemVO;
import com.mujian.backend.prop.vo.QrExceptionCountsVO;
import com.mujian.backend.prop.vo.QrExceptionPageVO;
import com.mujian.backend.prop.vo.QrExceptionVO;
import com.mujian.backend.prop.vo.ReviewBaseVO;
import com.mujian.backend.prop.vo.ReviewPageVO;
import com.mujian.backend.prop.vo.ReviewPendingCountsVO;
import com.mujian.backend.prop.vo.ReviewRowVO;
import com.mujian.backend.prop.vo.SkuReviewVO;
import com.mujian.backend.prop.vo.SnReviewVO;
import com.mujian.backend.prop.vo.StockReviewVO;
import java.util.*;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PropReviewQueryServiceImpl implements PropReviewQueryService {
  private final PropReviewMapper propReviewMapper;
  private final PropSnAdjustmentItemMapper propSnAdjustmentItemMapper;
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final PropQrExceptionRecordMapper propQrExceptionRecordMapper;
  private final PropSkuMapper propSkuMapper;
  private final PropQrCodeMapper propQrCodeMapper;
  private final PropAccess propAccess;
  private final PropReviewService propReviewService;
  private final PropSnapshots propSnapshots;
  private final PropQueryService propQueryService;
  private final PropInventory propInventory;
  private final PropCasework propCasework;
  private final PropMaterialService propMaterialService;
  private final PropJson propJson;
  private final java.time.Clock clock;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropReviewQueryServiceImpl(
      PropReviewMapper propReviewMapper,
      PropSnAdjustmentItemMapper propSnAdjustmentItemMapper,
      PropSnInstanceMapper propSnInstanceMapper,
      PropQrExceptionRecordMapper propQrExceptionRecordMapper,
      PropSkuMapper propSkuMapper,
      PropQrCodeMapper propQrCodeMapper,
      PropAccess propAccess,
      PropReviewService propReviewService,
      PropSnapshots propSnapshots,
      PropQueryService propQueryService,
      PropInventory propInventory,
      PropCasework propCasework,
      PropMaterialService propMaterialService,
      PropJson propJson,
      java.time.Clock clock) {
    this.clock = clock;
    this.propReviewMapper = propReviewMapper;
    this.propSnAdjustmentItemMapper = propSnAdjustmentItemMapper;
    this.propSnInstanceMapper = propSnInstanceMapper;
    this.propQrExceptionRecordMapper = propQrExceptionRecordMapper;
    this.propSkuMapper = propSkuMapper;
    this.propQrCodeMapper = propQrCodeMapper;
    this.propAccess = propAccess;
    this.propReviewService = propReviewService;
    this.propSnapshots = propSnapshots;
    this.propQueryService = propQueryService;
    this.propInventory = propInventory;
    this.propCasework = propCasework;
    this.propMaterialService = propMaterialService;
    this.propJson = propJson;
  }

  /** 校验申请访问范围并组装审核快照、当前状态和可执行操作。 */
  public ReviewBaseVO detail(String actor, String type, String id, boolean admin) {
    if (type.equals(QR_EXCEPTION)) throw ApiFailure.invalid();
    if (admin) propAccess.admin(actor, "prop-review", "read");
    PropReviewProjection propReviewProjection = propAccess.source(actor, type, id, admin);
    PropSkuDO propSkuDO = propAccess.sku(propReviewProjection.getSkuId());
    PropFactoryProjection propFactoryProjection =
        propAccess.factoryById(propSkuDO.getFactoryId(), false);
    boolean pending = CODE_PENDING.equals(propReviewProjection.getAuditStatus()),
        canReview = admin && propAccess.has(actor, "prop-review", "review"),
        preview = !admin || propAccess.has(actor, "prop-review", "sensitive");
    String block = null;
    if (pending) {
      try {
        propReviewService.approvable(type, propReviewProjection, propSkuDO);
      } catch (ApiFailure apiFailure) {
        if (apiFailure.status >= 500) throw apiFailure;
        block = apiFailure.reason;
      }
    }
    ReviewBaseVO reviewBaseVO =
        switch (type) {
          case SKU_AUDIT -> new SkuReviewVO();
          case SN_ADJUSTMENT -> new SnReviewVO();
          case STOCK_ADJUSTMENT -> new StockReviewVO();
          default -> throw ApiFailure.invalid();
        };
    List<PropApplicantProjection> propApplicantProjectionList =
        propReviewMapper.selectPropApplicantNameByUserId(propReviewProjection.getApplicantUserId());
    if (propApplicantProjectionList.isEmpty()) throw PropAccess.inconsistent();
    reviewBaseVO
        .id(id)
        .skuId(propSkuDO.getId())
        .factoryId(propFactoryProjection.getId())
        .factoryName(propFactoryProjection.getFactoryName())
        .applicantUserId(propReviewProjection.getApplicantUserId())
        .applicantName(propApplicantProjectionList.get(0).getNickname())
        .auditStatus(propReviewProjection.getAuditStatus())
        .version(propReviewProjection.getVersion())
        .applyRemark(
            (type.equals(STOCK_ADJUSTMENT)
                ? propReviewProjection.getReason()
                : propReviewProjection.getApplyRemark()))
        .auditRemark(propReviewProjection.getAuditRemark())
        .rejectReason(propReviewProjection.getRejectReason())
        .submittedAt(time(propReviewProjection.getSubmittedAt()))
        .reviewedAt(time(propReviewProjection.getReviewedAt()))
        .reviewerId(propReviewProjection.getReviewerId())
        .caseId(propCasework.caseId(type, id));
    boolean canCancel = false;
    if (!admin && pending) {
      try {
        propAccess.factoryAccess.context(actor, true);
        canCancel = true;
      } catch (ApiFailure apiFailureFailure) {
        if (apiFailureFailure.status >= 500) throw apiFailureFailure;
      }
    }
    reviewBaseVO
        .canCancel(canCancel)
        .canApprove(canReview && pending && block == null)
        .canReject(canReview && pending)
        .approvalBlockReason(block)
        .sourceType(type)
        .skuCode(propSkuDO.getSkuCode())
        .propName(
            type.equals(SKU_AUDIT)
                ? propSnapshots.fields(propReviewProjection.getProposedSnapshotJson()).getPropName()
                : propSkuDO.getPropName())
        .currentSkuStatus(propSkuDO.getSkuStatus())
        .currentSkuVersion(propSkuDO.getVersion())
        .assetType(propSkuDO.getAssetType());
    if (type.equals(SKU_AUDIT)) {
      {
        ((SkuReviewVO) reviewBaseVO).setActionType(propReviewProjection.getActionType());
        ((SkuReviewVO) reviewBaseVO).setAssetType(propSkuDO.getAssetType());
        ((SkuReviewVO) reviewBaseVO).setBeforeSkuStatus(propReviewProjection.getBeforeSkuStatus());
        ((SkuReviewVO) reviewBaseVO).setTargetSkuStatus(propReviewProjection.getTargetSkuStatus());
        ((SkuReviewVO) reviewBaseVO)
            .setBefore(
                propReviewProjection.getBeforeSnapshotJson() == null
                    ? null
                    : propSnapshots.read(
                        propSnapshots.fields(propReviewProjection.getBeforeSnapshotJson()),
                        type,
                        id,
                        "before",
                        preview));
        ((SkuReviewVO) reviewBaseVO)
            .setProposed(
                propSnapshots.read(
                    propSnapshots.fields(propReviewProjection.getProposedSnapshotJson()),
                    type,
                    id,
                    "proposed",
                    preview));
        ((SkuReviewVO) reviewBaseVO)
            .setCurrent(
                propSnapshots.read(
                    propSnapshots.current(propSkuDO), SKU_AUDIT, id, "current", preview));
        ((SkuReviewVO) reviewBaseVO).setCurrentFillStatus(propSkuDO.getFillStatus());
        ((SkuReviewVO) reviewBaseVO).setCurrentUpdatedAt(time(propSkuDO.getUpdatedAt()));
        ((SkuReviewVO) reviewBaseVO)
            .setInitialQuantity(
                (propSkuDO.getAssetType().equals(PropAssetType.A_ASSET.name())
                    ? propReviewProjection.getInitialSnQuantity()
                    : propReviewProjection.getInitialStockQuantity()));
        ((SkuReviewVO) reviewBaseVO).setBaseSkuVersion(propReviewProjection.getBaseSkuVersion());
        ((SkuReviewVO) reviewBaseVO)
            .setPreviousApplicationId(propReviewProjection.getPreviousAuditId());
      }
    } else if (type.equals(SN_ADJUSTMENT)) {
      List<PropSnAdjustmentItemDO> propSnAdjustmentItemDOList =
          propSnAdjustmentItemMapper.selectReviewSnAdjustmentItemListByAuditId(id);
      HashMap<String, PropSnInstanceDO> propSnInstanceDOMap =
          new HashMap<String, PropSnInstanceDO>();
      List<String> snIds = new ArrayList<>();
      for (PropSnAdjustmentItemDO propSnAdjustmentItemDO : propSnAdjustmentItemDOList)
        snIds.add(propSnAdjustmentItemDO.getSnId());
      for (int start = 0; start < snIds.size(); start += 500) {
        for (PropSnInstanceDO propSnInstanceDO :
            propSnInstanceMapper.selectPropSnInstanceListByIds(
                snIds.subList(start, Math.min(start + 500, snIds.size()))))
          propSnInstanceDOMap.put(propSnInstanceDO.getId(), propSnInstanceDO);
      }
      List<AdjustmentItemVO> adjustmentItemVOList = new ArrayList<>();
      for (PropSnAdjustmentItemDO propSnAdjustmentItemDO : propSnAdjustmentItemDOList) {
        PropSnInstanceDO propSnInstanceDO =
            propSnInstanceDOMap.get(propSnAdjustmentItemDO.getSnId());
        if (propSnInstanceDO == null) throw PropAccess.inconsistent();
        adjustmentItemVOList.add(
            new AdjustmentItemVO()
                .snId(propSnAdjustmentItemDO.getSnId())
                .snCodeSnapshot(propSnAdjustmentItemDO.getSnCodeSnapshot())
                .beforeStatus(propSnAdjustmentItemDO.getBeforeInstanceStatus())
                .targetStatus(propSnAdjustmentItemDO.getTargetInstanceStatus())
                .remark(propSnAdjustmentItemDO.getItemRemark())
                .currentStatus(propSnInstanceDO.getInstanceStatus())
                .currentStatusRemark(propSnInstanceDO.getStatusRemark())
                .currentStatusChangedAt(time(propSnInstanceDO.getStatusChangedAt())));
      }
      {
        ((SnReviewVO) reviewBaseVO).setAdjustmentType(propReviewProjection.getAdjustmentType());
        ((SnReviewVO) reviewBaseVO).setIncreaseScene(propReviewProjection.getIncreaseScene());
        ((SnReviewVO) reviewBaseVO).setQuantity(propReviewProjection.getRequestedQuantity());
        ((SnReviewVO) reviewBaseVO).setItems(adjustmentItemVOList);
        ((SnReviewVO) reviewBaseVO)
            .setAbnormalResult(
                propReviewProjection.getAdjustmentType().equals("abnormal_decrease")
                        && !propSnAdjustmentItemDOList.isEmpty()
                    ? propSnAdjustmentItemDOList.get(0).getTargetInstanceStatus()
                    : null);
        ((SnReviewVO) reviewBaseVO)
            .setCurrentSummary(propQueryService.summaryDto(propSkuDO.getId()));
        ((SnReviewVO) reviewBaseVO)
            .setRelatedSkuAuditId(propReviewProjection.getRelatedSkuAuditId());
      }
    } else {
      ((StockReviewVO) reviewBaseVO).setAdjustmentType(propReviewProjection.getAdjustmentType());
      ((StockReviewVO) reviewBaseVO).setQuantityDelta(propReviewProjection.getQuantityDelta());
      ((StockReviewVO) reviewBaseVO).setAbnormalResult(propReviewProjection.getAbnormalResult());
      ((StockReviewVO) reviewBaseVO)
          .setBeforeTotalQuantity(propReviewProjection.getBeforeTotalQuantity());
      ((StockReviewVO) reviewBaseVO)
          .setBeforeAvailableQuantity(propReviewProjection.getBeforeAvailableQuantity());
      ((StockReviewVO) reviewBaseVO)
          .setCurrentStock(
              propQueryService.stockDto(propInventory.stock(propSkuDO.getId(), false)));
      ((StockReviewVO) reviewBaseVO)
          .setRelatedSkuAuditId(propReviewProjection.getRelatedSkuAuditId());
    }
    return reviewBaseVO;
  }

  /** 按审核条件分页查询申请，并返回各类待审数量。 */
  public ReviewPageVO list(String actor, ReviewQueryDTO reviewQueryDTO, boolean admin) {
    if (admin) propAccess.admin(actor, "prop-review", "read");
    String factory =
        admin ? reviewQueryDTO.getFactoryId() : propAccess.factory(actor, false, false).getId();
    reviewQueryDTO.setFactory(factory);
    reviewQueryDTO.setStatus(null);
    if ((reviewQueryDTO.getKeyword() != null))
      reviewQueryDTO.setKeyword(text(reviewQueryDTO.getKeyword(), 128));
    if ((reviewQueryDTO.getFactoryKeyword() != null))
      reviewQueryDTO.setFactoryKeyword(text(reviewQueryDTO.getFactoryKeyword(), 128));
    if ((reviewQueryDTO.getReviewCategory() != null))
      reviewQueryDTO.setCategory(reviewQueryDTO.getReviewCategory());
    ReviewPendingCountsVO reviewPendingCountsVO =
        new ReviewPendingCountsVO().total("0").sku("0").inventory("0").skuStatus("0");
    long pending = 0;
    for (PropReviewCountProjection propReviewCountProjection :
        propReviewMapper.selectPropReviewPendingCountByQuery(reviewQueryDTO)) {
      long n = propReviewCountProjection.getAmount();
      pending += n;
      switch (propReviewCountProjection.getCategory()) {
        case "sku_status" -> reviewPendingCountsVO.setSkuStatus(Long.toString(n));
        case "sku" -> reviewPendingCountsVO.setSku(Long.toString(n));
        case "inventory" -> reviewPendingCountsVO.setInventory(Long.toString(n));
        default -> throw PropAccess.inconsistent();
      }
    }
    reviewPendingCountsVO.setTotal(Long.toString(pending));
    String status =
        (reviewQueryDTO.getAuditStatus() == null
            ? admin ? CODE_PENDING : "all"
            : reviewQueryDTO.getAuditStatus());
    if (!status.equals("all")) {
      reviewQueryDTO.setStatus(status);
    }
    long total = propReviewMapper.selectPropReviewCountByQuery(reviewQueryDTO);
    reviewQueryDTO.setOffset(com.mujian.backend.prop.utils.PropPageUtils.offset(reviewQueryDTO));
    reviewQueryDTO.setLimit(
        com.mujian.backend.prop.utils.PropPageUtils.number(reviewQueryDTO, "pageSize", 20));
    List<PropReviewListProjection> propReviewListProjectionList =
        propReviewMapper.selectPropReviewPageByQuery(reviewQueryDTO);
    List<ReviewRowVO> reviewRowVOList = new ArrayList<>();
    for (PropReviewListProjection propReviewListProjection : propReviewListProjectionList) {
      reviewRowVOList.add(
          new ReviewRowVO()
              .id(propReviewListProjection.getId())
              .sourceType(propReviewListProjection.getSourceType())
              .skuId(propReviewListProjection.getSkuId())
              .skuCode(propReviewListProjection.getSkuCode())
              .propName(propReviewListProjection.getPropName())
              .factoryId(propReviewListProjection.getFactoryId())
              .factoryName(propReviewListProjection.getFactoryName())
              .assetType(propReviewListProjection.getAssetType())
              .actionType(propReviewListProjection.getActionType())
              .auditStatus(propReviewListProjection.getAuditStatus())
              .submittedAt(time(propReviewListProjection.getSubmittedAt()))
              .reviewedAt(time(propReviewListProjection.getReviewedAt()))
              .applicantName(propReviewListProjection.getApplicantName())
              .applyRemark(propReviewListProjection.getApplyRemark())
              .version(propReviewListProjection.getVersion())
              .initialQuantity(propReviewListProjection.getInitialQuantity())
              .quantity(propReviewListProjection.getQuantity())
              .quantityDelta(propReviewListProjection.getQuantityDelta())
              .relatedSkuAuditId(propReviewListProjection.getRelatedSkuAuditId()));
    }
    PageResult<ReviewRowVO> pageResult =
        com.mujian.backend.prop.utils.PropPageUtils.page(reviewRowVOList, total, reviewQueryDTO);
    return new ReviewPageVO(
        pageResult.items(),
        pageResult.total(),
        pageResult.page(),
        pageResult.pageSize(),
        reviewPendingCountsVO);
  }

  /** 校验管理权限后读取二维码异常处理详情。 */
  public QrExceptionVO exception(String actor, String id) {
    propAccess.admin(actor, "prop-qr", "read");
    List<PropQrExceptionProjection> propQrExceptionProjectionList =
        propQrExceptionRecordMapper.selectQrExceptionDetailById(id(id));
    if (propQrExceptionProjectionList.isEmpty()) throw missing();
    PropQrExceptionProjection propQrExceptionProjection = propQrExceptionProjectionList.get(0);

    boolean pending = propQrExceptionProjection.getExceptionStatus().equals("reported"),
        review = propAccess.has(actor, "prop-qr", "review");
    QrExceptionVO qrExceptionVO =
        new QrExceptionVO()
            .id(propQrExceptionProjection.getId())
            .exceptionNo(propQrExceptionProjection.getExceptionNo())
            .factoryName(propQrExceptionProjection.getFactoryName())
            .skuCode(propQrExceptionProjection.getSkuCode())
            .propName(propQrExceptionProjection.getPropName())
            .snCode(propQrExceptionProjection.getSnCode())
            .qrCode(propQrExceptionProjection.getQrCodeId())
            .resolvedByAdminUserId(propQrExceptionProjection.getResolvedByAdminUserId())
            .replacementQrId(propQrExceptionProjection.getReplacementQrCodeId())
            .factoryId(propQrExceptionProjection.getFactoryId())
            .skuId(propQrExceptionProjection.getSkuId())
            .snId(propQrExceptionProjection.getSnId())
            .originalQrId(propQrExceptionProjection.getOriginalQrCodeId())
            .reportedByUserId(propQrExceptionProjection.getReportedByUserId())
            .exceptionType(propQrExceptionProjection.getExceptionType())
            .description(propQrExceptionProjection.getDescription())
            .evidence(
                propMaterialService.images(
                    propJson.strings(propQrExceptionProjection.getEvidenceUrlsJson()),
                    QR_EXCEPTION,
                    propQrExceptionProjection.getId(),
                    "evidence",
                    propAccess.has(actor, "prop-qr", "sensitive")))
            .exceptionStatus(propQrExceptionProjection.getExceptionStatus())
            .resolutionType(propQrExceptionProjection.getResolutionType())
            .resolutionReason(propQrExceptionProjection.getResolutionReason())
            .resolvedAt(time(propQrExceptionProjection.getResolvedAt()))
            .version(propQrExceptionProjection.getVersion())
            .createdAt(time(propQrExceptionProjection.getCreatedAt()));
    boolean canApprove = false;
    if (pending && review) {
      PropReviewProjection propReviewProjection =
          PropReviewConverter.from(propQrExceptionProjection);
      PropSkuDO propSkuDO = propAccess.sku(propQrExceptionProjection.getSkuId());
      try {
        propReviewService.approvable(QR_EXCEPTION, propReviewProjection, propSkuDO);
        canApprove = true;
      } catch (ApiFailure apiFailure) {
        if (apiFailure.status >= 500) throw apiFailure;
      }
    }
    return qrExceptionVO.canApprove(canApprove).canReject(pending && review);
  }

  /** 查询二维码异常列表及状态分类数量。 */
  public QrExceptionPageVO exceptions(String actor, QrExceptionQueryDTO qrExceptionQueryDTO) {
    propAccess.admin(actor, "prop-qr", "read");
    String requestedStatus = qrExceptionQueryDTO.getStatus();
    qrExceptionQueryDTO.setStatus(null);
    if ((qrExceptionQueryDTO.getFactoryKeyword() != null))
      qrExceptionQueryDTO.setFactoryKeyword(text(qrExceptionQueryDTO.getFactoryKeyword(), 128));
    if ((qrExceptionQueryDTO.getKeyword() != null))
      qrExceptionQueryDTO.setKeyword(text(qrExceptionQueryDTO.getKeyword(), 128));
    qrExceptionQueryDTO.setToday(now(clock).toLocalDate().atStartOfDay());
    qrExceptionQueryDTO.setTomorrow(now(clock).toLocalDate().plusDays(1).atStartOfDay());
    PropQrExceptionStatsProjection propQrExceptionStatsProjection =
        propQrExceptionRecordMapper.selectQrExceptionStatsByQuery(qrExceptionQueryDTO).get(0);
    QrExceptionCountsVO qrExceptionCountsVO =
        new QrExceptionCountsVO()
            .reported(Integer.toString(propQrExceptionStatsProjection.getReported()))
            .suspectedLeak(Integer.toString(propQrExceptionStatsProjection.getSuspectedLeak()))
            .resolvedToday(Integer.toString(propQrExceptionStatsProjection.getResolvedToday()))
            .cancelledToday(Integer.toString(propQrExceptionStatsProjection.getCancelledToday()));
    String status = (requestedStatus == null ? "reported" : requestedStatus);
    if (!status.equals("all")) {
      qrExceptionQueryDTO.setStatus(status);
    }
    long total = propQrExceptionRecordMapper.selectQrExceptionCountByQuery(qrExceptionQueryDTO);
    qrExceptionQueryDTO.setOffset(
        com.mujian.backend.prop.utils.PropPageUtils.offset(qrExceptionQueryDTO));
    qrExceptionQueryDTO.setLimit(
        com.mujian.backend.prop.utils.PropPageUtils.number(qrExceptionQueryDTO, "pageSize", 20));
    List<PropQrExceptionProjection> propQrExceptionProjectionList =
        propQrExceptionRecordMapper.selectQrExceptionPageByQuery(qrExceptionQueryDTO);
    boolean canReview =
        !propQrExceptionProjectionList.isEmpty() && propAccess.has(actor, "prop-qr", "review");
    boolean canPreview =
        !propQrExceptionProjectionList.isEmpty() && propAccess.has(actor, "prop-qr", "sensitive");
    // 仅待处理且有审核权限的记录需要可批准判断，集中准备SKU与二维码。
    Set<String> skuIds = new LinkedHashSet<>(), qrIds = new LinkedHashSet<>();
    for (PropQrExceptionProjection propQrExceptionProjection : propQrExceptionProjectionList) {
      if (canReview && "reported".equals(propQrExceptionProjection.getExceptionStatus())) {
        skuIds.add(propQrExceptionProjection.getSkuId());
        qrIds.add(propQrExceptionProjection.getOriginalQrCodeId());
      }
    }
    Map<String, PropSkuDO> propSkuDOMap = new HashMap<>();
    Map<String, PropQrCodeDO> propQrCodeDOMap = new HashMap<>();
    if (!skuIds.isEmpty()) {
      for (PropSkuDO propSkuDO : propSkuMapper.selectPropSkuListByIds(new ArrayList<>(skuIds)))
        propSkuDOMap.put(propSkuDO.getId(), propSkuDO);
      for (PropQrCodeDO propQrCodeDO :
          propQrCodeMapper.selectPropQrCodeListByIds(new ArrayList<>(qrIds)))
        propQrCodeDOMap.put(propQrCodeDO.getId(), propQrCodeDO);
    }
    List<QrExceptionVO> qrExceptionVOList = new ArrayList<>();
    for (PropQrExceptionProjection propQrExceptionProjection : propQrExceptionProjectionList) {
      boolean pending = propQrExceptionProjection.getExceptionStatus().equals("reported"),
          review = canReview;
      QrExceptionVO qrExceptionVO =
          new QrExceptionVO()
              .id(propQrExceptionProjection.getId())
              .exceptionNo(propQrExceptionProjection.getExceptionNo())
              .factoryName(propQrExceptionProjection.getFactoryName())
              .skuCode(propQrExceptionProjection.getSkuCode())
              .propName(propQrExceptionProjection.getPropName())
              .snCode(propQrExceptionProjection.getSnCode())
              .qrCode(propQrExceptionProjection.getQrCodeId())
              .resolvedByAdminUserId(propQrExceptionProjection.getResolvedByAdminUserId())
              .replacementQrId(propQrExceptionProjection.getReplacementQrCodeId())
              .factoryId(propQrExceptionProjection.getFactoryId())
              .skuId(propQrExceptionProjection.getSkuId())
              .snId(propQrExceptionProjection.getSnId())
              .originalQrId(propQrExceptionProjection.getOriginalQrCodeId())
              .reportedByUserId(propQrExceptionProjection.getReportedByUserId())
              .exceptionType(propQrExceptionProjection.getExceptionType())
              .description(propQrExceptionProjection.getDescription())
              .evidence(
                  propMaterialService.images(
                      propJson.strings(propQrExceptionProjection.getEvidenceUrlsJson()),
                      QR_EXCEPTION,
                      propQrExceptionProjection.getId(),
                      "evidence",
                      canPreview))
              .exceptionStatus(propQrExceptionProjection.getExceptionStatus())
              .resolutionType(propQrExceptionProjection.getResolutionType())
              .resolutionReason(propQrExceptionProjection.getResolutionReason())
              .resolvedAt(time(propQrExceptionProjection.getResolvedAt()))
              .version(propQrExceptionProjection.getVersion())
              .createdAt(time(propQrExceptionProjection.getCreatedAt()));
      boolean canApprove = false;
      if (pending && review) {
        PropReviewProjection propReviewProjection =
            PropReviewConverter.from(propQrExceptionProjection);
        PropSkuDO propSkuDO = propSkuDOMap.get(propQrExceptionProjection.getSkuId());
        if (propSkuDO == null) throw missing();
        try {
          com.mujian.backend.prop.policy.PropQrReviewPolicy.assertApprovable(
              propReviewProjection,
              propSkuDO,
              propQrCodeDOMap.get(propQrExceptionProjection.getOriginalQrCodeId()));
          canApprove = true;
        } catch (ApiFailure apiFailure) {
          if (apiFailure.status >= 500) throw apiFailure;
        }
      }
      qrExceptionVOList.add(qrExceptionVO.canApprove(canApprove).canReject(pending && review));
    }
    PageResult<QrExceptionVO> pageResult =
        com.mujian.backend.prop.utils.PropPageUtils.page(
            qrExceptionVOList, total, qrExceptionQueryDTO);
    return new QrExceptionPageVO(
        pageResult.items(),
        pageResult.total(),
        pageResult.page(),
        pageResult.pageSize(),
        qrExceptionCountsVO);
  }
}
