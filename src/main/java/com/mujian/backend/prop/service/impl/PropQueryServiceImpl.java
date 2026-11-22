package com.mujian.backend.prop.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.prop.utils.PropPageUtils.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.enums.DictionaryGroup;
import com.mujian.backend.common.enums.PropAssetType;
import com.mujian.backend.common.enums.PropQrTargetType;
import com.mujian.backend.prop.converter.OwnedSkuConverter;
import com.mujian.backend.prop.domain.PropImageDO;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.domain.PropSkuAuditDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSkuStockDO;
import com.mujian.backend.prop.domain.PropSnInstanceDO;
import com.mujian.backend.prop.dto.InstanceQueryDTO;
import com.mujian.backend.prop.dto.LogQueryDTO;
import com.mujian.backend.prop.dto.MovementQueryDTO;
import com.mujian.backend.prop.dto.OwnedQueryDTO;
import com.mujian.backend.prop.dto.PropRentalPeriodQueryDTO;
import com.mujian.backend.prop.dto.PropSkuFieldsDTO;
import com.mujian.backend.prop.dto.ReservationQueryDTO;
import com.mujian.backend.prop.mapper.InventoryReservationMapper;
import com.mujian.backend.prop.mapper.PropImageMapper;
import com.mujian.backend.prop.mapper.PropInstanceStatusLogMapper;
import com.mujian.backend.prop.mapper.PropSkuAuditMapper;
import com.mujian.backend.prop.mapper.PropSkuMapper;
import com.mujian.backend.prop.mapper.PropSkuStyleMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentAuditMapper;
import com.mujian.backend.prop.mapper.PropSnInstanceMapper;
import com.mujian.backend.prop.mapper.PropStockMovementMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.policy.PropAccess.FactoryReadContext;
import com.mujian.backend.prop.policy.PropAvailability;
import com.mujian.backend.prop.policy.PropAvailability.Dates;
import com.mujian.backend.prop.projection.PropFactoryProjection;
import com.mujian.backend.prop.projection.PropInstanceCountProjection;
import com.mujian.backend.prop.projection.PropInventorySummaryProjection;
import com.mujian.backend.prop.projection.PropSnPendingProjection;
import com.mujian.backend.prop.projection.PropStyleProjection;
import com.mujian.backend.prop.service.PropCatalogService;
import com.mujian.backend.prop.service.PropInventory;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.service.PropQrLookup;
import com.mujian.backend.prop.service.PropQrLookup.Chain;
import com.mujian.backend.prop.service.PropQueryService;
import com.mujian.backend.prop.service.PropSnapshots;
import com.mujian.backend.prop.utils.PropJson;
import com.mujian.backend.prop.vo.AvailabilityVO;
import com.mujian.backend.prop.vo.FactoryPublicVO;
import com.mujian.backend.prop.vo.MovementVO;
import com.mujian.backend.prop.vo.OptionVO;
import com.mujian.backend.prop.vo.OwnedSkuVO;
import com.mujian.backend.prop.vo.PropDetailCardVO;
import com.mujian.backend.prop.vo.PropDetailDetailsVO;
import com.mujian.backend.prop.vo.PropDetailVO;
import com.mujian.backend.prop.vo.ReservationVO;
import com.mujian.backend.prop.vo.SnLogVO;
import com.mujian.backend.prop.vo.SnSummaryVO;
import com.mujian.backend.prop.vo.SnVO;
import com.mujian.backend.prop.vo.StockVO;
import java.util.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PropQueryServiceImpl implements PropQueryService {
  private final PropImageMapper propImageMapper;
  private final PropSkuStyleMapper propSkuStyleMapper;
  private final PropSkuAuditMapper propSkuAuditMapper;
  private final PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper;
  private final InventoryReservationMapper inventoryReservationMapper;
  private final PropInstanceStatusLogMapper propInstanceStatusLogMapper;
  private final PropStockMovementMapper propStockMovementMapper;
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final PropSkuMapper propSkuMapper;
  private final PropAccess propAccess;
  private final PropSnapshots propSnapshots;
  private final PropInventory propInventory;
  private final PropMaterialService propMaterialService;
  private final PropCatalogService propCatalogService;
  private final PropAvailability propAvailability;
  private final PropJson propJson;
  private final PropQrLookup propQrLookup;
  private final java.time.Clock clock;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropQueryServiceImpl(
      PropImageMapper propImageMapper,
      PropSkuStyleMapper propSkuStyleMapper,
      PropSkuAuditMapper propSkuAuditMapper,
      PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper,
      InventoryReservationMapper inventoryReservationMapper,
      PropInstanceStatusLogMapper propInstanceStatusLogMapper,
      PropStockMovementMapper propStockMovementMapper,
      PropSnInstanceMapper propSnInstanceMapper,
      PropSkuMapper propSkuMapper,
      PropAccess propAccess,
      PropSnapshots propSnapshots,
      PropInventory propInventory,
      PropMaterialService propMaterialService,
      PropCatalogService propCatalogService,
      PropAvailability propAvailability,
      PropJson propJson,
      PropQrLookup propQrLookup,
      java.time.Clock clock) {
    this.clock = clock;
    this.propQrLookup = propQrLookup;
    this.propImageMapper = propImageMapper;
    this.propSkuStyleMapper = propSkuStyleMapper;
    this.propSkuAuditMapper = propSkuAuditMapper;
    this.propSnAdjustmentAuditMapper = propSnAdjustmentAuditMapper;
    this.inventoryReservationMapper = inventoryReservationMapper;
    this.propInstanceStatusLogMapper = propInstanceStatusLogMapper;
    this.propStockMovementMapper = propStockMovementMapper;
    this.propSnInstanceMapper = propSnInstanceMapper;
    this.propSkuMapper = propSkuMapper;
    this.propAccess = propAccess;
    this.propSnapshots = propSnapshots;
    this.propInventory = propInventory;
    this.propMaterialService = propMaterialService;
    this.propCatalogService = propCatalogService;
    this.propAvailability = propAvailability;
    this.propJson = propJson;
  }

  /** 判断道具当前是否满足公开浏览和租赁准入条件。 */
  public boolean eligible(PropSkuDO propSkuDO) {
    return "online".equals(propSkuDO.getSkuStatus())
        && propAccess.pending(propSkuDO.getId()) == null
        && CODE_ACTIVE.equals(
            propAccess.factoryById(propSkuDO.getFactoryId(), false).getFactoryStatus());
  }

  /** 校验道具可见性并返回公开资料与可选租期结果。 */
  public PropDetailVO detail(
      String actor, String id, PropRentalPeriodQueryDTO propRentalPeriodQueryDTO) {
    if (actor != null) propAccess.demander(actor);
    PropSkuDO propSkuDO = propAccess.sku(id);
    if (!"online".equals(propSkuDO.getSkuStatus()) || propAccess.pending(propSkuDO.getId()) != null)
      throw missing();
    PropFactoryProjection propFactoryProjection =
        propAccess.factoryById(propSkuDO.getFactoryId(), false);
    if (!CODE_ACTIVE.equals(propFactoryProjection.getFactoryStatus())) throw missing();
    Dates dates =
        propAvailability.dates(
            propRentalPeriodQueryDTO.getStartDate(), propRentalPeriodQueryDTO.getEndDate(), false);
    PropSkuFieldsDTO propSkuFieldsDTO = propSnapshots.currentValues(propSkuDO);
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = propCatalogService.snapshot();
    List<OptionVO> optionVOListStyles = new ArrayList<>();
    for (String code : propSkuFieldsDTO.getStyleCodes()) {
      optionVOListStyles.add(
          propCatalogService.displayOption(
              dictionarySnapshot, DictionaryGroup.PROP_SKU_STYLE, code));
    }
    PropDetailDetailsVO propDetailDetailsVO = new PropDetailDetailsVO();
    propDetailDetailsVO.setPropName(propSkuFieldsDTO.getPropName());
    propDetailDetailsVO.setSizeDesc(propSkuFieldsDTO.getSizeDesc());
    propDetailDetailsVO.setLengthCm(propSkuFieldsDTO.getLengthCm());
    propDetailDetailsVO.setWidthCm(propSkuFieldsDTO.getWidthCm());
    propDetailDetailsVO.setHeightCm(propSkuFieldsDTO.getHeightCm());
    propDetailDetailsVO.setMaterialDesc(propSkuFieldsDTO.getMaterialDesc());
    propDetailDetailsVO.setWeightKg(propSkuFieldsDTO.getWeightKg());
    propDetailDetailsVO.setTransportSuggestion(propSkuFieldsDTO.getTransportSuggestion());
    propDetailDetailsVO.setDailyRentPriceFen(propSkuFieldsDTO.getDailyRentPriceFen());
    propDetailDetailsVO.setDepositAmountFen(propSkuFieldsDTO.getDepositAmountFen());
    // 同一响应中轮播与封面相同路径只签一次；未公开图片不进入此分支。
    Map<String, com.mujian.backend.prop.vo.PublicImageViewVO> publicImageViewVOMap =
        new HashMap<>();
    List<com.mujian.backend.prop.vo.PublicImageViewVO> publicImageViewVOList = new ArrayList<>();
    for (String key : propSkuFieldsDTO.getImageKeys()) {
      if (!publicImageViewVOMap.containsKey(key)) {
        publicImageViewVOMap.put(key, propMaterialService.publicImage(key));
      }
      publicImageViewVOList.add(publicImageViewVOMap.get(key));
    }
    if (!publicImageViewVOMap.containsKey(propSkuDO.getImageUrl())) {
      publicImageViewVOMap.put(
          propSkuDO.getImageUrl(), propMaterialService.publicImage(propSkuDO.getImageUrl()));
    }
    propDetailDetailsVO.setImages(publicImageViewVOList);
    propDetailDetailsVO.setFireResistance(
        propCatalogService.displayOption(
            dictionarySnapshot,
            DictionaryGroup.PROP_SKU_FIRE_RESISTANT,
            propSkuDO.getFireResistantCode()));

    PropDetailCardVO propDetailCardVO =
        new PropDetailCardVO()
            .skuId(propSkuDO.getId())
            .propName(propSkuDO.getPropName())
            .assetType(propSkuDO.getAssetType())
            .cover(publicImageViewVOMap.get(propSkuDO.getImageUrl()))
            .type(
                propCatalogService.displayOption(
                    dictionarySnapshot, DictionaryGroup.PROP_SKU_TYPE, propSkuDO.getTypeCode()))
            .styles(optionVOListStyles)
            .dailyRentPriceFen(propSkuDO.getDailyRentPriceFen())
            .depositAmountFen(propSkuDO.getDepositAmountFen())
            .factory(
                new FactoryPublicVO()
                    .id(propFactoryProjection.getId())
                    .name(propFactoryProjection.getFactoryName())
                    .avatar(
                        propMaterialService.optional(propFactoryProjection.getFactoryAvatarUrl())));

    return new PropDetailVO()
        .prop(propDetailCardVO)
        .details(propDetailDetailsVO)
        .availability(
            dates == null
                ? null
                : propAvailability.result(
                    propSkuDO,
                    dates,
                    propAvailability.quantities(List.of(id), dates).getOrDefault(id, 0),
                    true));
  }

  /** 区分管理员与工厂权限后取得可访问的道具。 */
  public PropSkuDO authorized(String actor, String id, boolean admin) {
    if (admin) {
      propAccess.admin(actor, "prop-asset", "read");
      return propAccess.sku(id);
    }
    return propAccess.own(actor, id);
  }

  /** 读取并组装当前权限范围内的道具详情。 */
  public OwnedSkuVO owned(String actor, String id, boolean admin) {
    FactoryReadContext factoryReadContext = null;
    PropSkuDO propSkuDO;
    if (admin) {
      propAccess.admin(actor, "prop-asset", "read");
      propSkuDO = propAccess.sku(id);
    } else {
      factoryReadContext = propAccess.factoryReadContext(actor);
      propSkuDO = propAccess.sku(id);
      if (!factoryReadContext.factory().getId().equals(propSkuDO.getFactoryId())) throw missing();
    }

    String skuId = propSkuDO.getId();
    List<PropInventorySummaryProjection> propInventorySummaryProjectionList =
        propSkuMapper.selectPropInventorySummaryListBySkuIds(
            List.of(skuId), now(clock).toLocalDate());
    if (propInventorySummaryProjectionList.isEmpty()) throw PropAccess.inconsistent();
    Chain chain = null;
    if (PropAssetType.B_CONSUMABLE.name().equals(propSkuDO.getAssetType()))
      chain = propQrLookup.find(PropQrTargetType.SKU.name(), List.of(skuId)).get(skuId);
    PropSkuAuditDO propSkuAuditDO = propAccess.pending(skuId);
    boolean writable = propAccess.canOperate(factoryReadContext, propSkuDO, propSkuAuditDO != null);
    boolean preview = !admin || propAccess.has(actor, "prop-asset", "sensitive");
    return OwnedSkuConverter.convert(
        propSkuDO,
        propInventorySummaryProjectionList.get(0),
        propSkuAuditDO,
        chain,
        propSnapshots.read(propSnapshots.current(propSkuDO), "sku", skuId, "current", preview),
        writable);
  }

  /** 分页查询工厂道具并批量补齐库存及二维码信息。 */
  public PageResult<OwnedSkuVO> ownedList(
      String actor, OwnedQueryDTO ownedQueryDTO, boolean admin) {
    FactoryReadContext factoryReadContext = null;
    if (admin) propAccess.admin(actor, "prop-asset", "read");
    else {
      factoryReadContext = propAccess.factoryReadContext(actor);
      ownedQueryDTO.setOwnerFactory(factoryReadContext.factory().getId());
    }
    ownedQueryDTO.setAdmin(admin);
    if (admin) ownedQueryDTO.setOwnerFactory(null);
    if ((ownedQueryDTO.getKeyword() != null))
      ownedQueryDTO.setKeyword(text(ownedQueryDTO.getKeyword(), 128));
    if ((ownedQueryDTO.getFactoryKeyword() != null))
      ownedQueryDTO.setFactoryKeyword(text(ownedQueryDTO.getFactoryKeyword(), 128));
    long total = propSkuMapper.selectOwnedPropSkuCountByQuery(ownedQueryDTO);
    ownedQueryDTO.setOffset(offset(ownedQueryDTO));
    ownedQueryDTO.setLimit(number(ownedQueryDTO, "pageSize", 20));
    List<PropSkuDO> propSkuDOList =
        ownedQueryDTO.getOffset() >= total
            ? List.of()
            : propSkuMapper.selectOwnedPropSkuPageByQuery(ownedQueryDTO);

    List<String> skuIds = new ArrayList<>(), consumableSkuIds = new ArrayList<>();
    for (PropSkuDO propSkuDO : propSkuDOList) {
      skuIds.add(propSkuDO.getId());
      if (PropAssetType.B_CONSUMABLE.name().equals(propSkuDO.getAssetType()))
        consumableSkuIds.add(propSkuDO.getId());
    }
    // 页大小已限制为100：关联信息整页批取，空页不查关联表。
    Map<String, PropInventorySummaryProjection> propInventorySummaryProjectionMap = new HashMap<>();
    Map<String, PropSkuAuditDO> propSkuAuditDOMap = new HashMap<>();
    Map<String, List<String>> styleCodesMap = new HashMap<>(), imageKeysMap = new HashMap<>();
    if (!skuIds.isEmpty()) {
      for (PropInventorySummaryProjection propInventorySummaryProjection :
          propSkuMapper.selectPropInventorySummaryListBySkuIds(skuIds, now(clock).toLocalDate()))
        propInventorySummaryProjectionMap.put(
            propInventorySummaryProjection.getId(), propInventorySummaryProjection);
      for (PropSkuAuditDO propSkuAuditDO :
          propSkuAuditMapper.selectPendingPropSkuAuditListBySkuIds(skuIds))
        propSkuAuditDOMap.putIfAbsent(propSkuAuditDO.getSkuId(), propSkuAuditDO);
      for (PropStyleProjection propStyleProjection :
          propSkuStyleMapper.selectPropSkuStyleListBySkuIds(skuIds)) {
        List<String> styleCodes = styleCodesMap.get(propStyleProjection.skuId());
        if (styleCodes == null) {
          styleCodes = new ArrayList<>();
          styleCodesMap.put(propStyleProjection.skuId(), styleCodes);
        }
        styleCodes.add(propStyleProjection.styleCode());
      }
      for (PropImageDO propImageDO : propImageMapper.selectPropImageListBySkuIds(skuIds)) {
        List<String> imageKeys = imageKeysMap.get(propImageDO.getSkuId());
        if (imageKeys == null) {
          imageKeys = new ArrayList<>();
          imageKeysMap.put(propImageDO.getSkuId(), imageKeys);
        }
        imageKeys.add(propImageDO.getImageUrl());
      }
    }
    Map<String, Chain> chainMap = propQrLookup.find(PropQrTargetType.SKU.name(), consumableSkuIds);
    boolean preview =
        !admin || (!skuIds.isEmpty() && propAccess.has(actor, "prop-asset", "sensitive"));
    List<OwnedSkuVO> ownedSkuVOList = new ArrayList<>();
    for (PropSkuDO propSkuDO : propSkuDOList) {
      String skuId = propSkuDO.getId();
      PropSkuAuditDO propSkuAuditDO = propSkuAuditDOMap.get(skuId);
      boolean writable =
          propAccess.canOperate(factoryReadContext, propSkuDO, propSkuAuditDO != null);
      PropSkuFieldsDTO propSkuFieldsDTO =
          propSnapshots.current(
              propSkuDO,
              styleCodesMap.getOrDefault(skuId, List.of()),
              imageKeysMap.getOrDefault(skuId, List.of()));
      ownedSkuVOList.add(
          OwnedSkuConverter.convert(
              propSkuDO,
              propInventorySummaryProjectionMap.get(skuId),
              propSkuAuditDO,
              chainMap.get(skuId),
              propSnapshots.read(propSkuFieldsDTO, "sku", skuId, "current", preview),
              writable));
    }
    return page(ownedSkuVOList, total, ownedQueryDTO);
  }

  /** 将库存台账转换为接口字段，不额外查询数据库。 */
  public StockVO stockDto(PropSkuStockDO propSkuStockDO) {
    return com.mujian.backend.prop.converter.PropReviewConverter.stock(propSkuStockDO);
  }

  /** 读取道具的库存台账并校验数据唯一性。 */
  public StockVO stock(String actor, String id, boolean admin) {
    PropSkuDO propSkuDO;
    if (admin) {
      propAccess.admin(actor, "prop-asset", "read");
      propSkuDO = propAccess.sku(id);
    } else {
      propSkuDO = propAccess.own(actor, id);
    }
    if (!propSkuDO.getAssetType().equals(PropAssetType.B_CONSUMABLE.name())) throw missing();
    return com.mujian.backend.prop.converter.PropReviewConverter.stock(
        propInventory.stock(id, false));
  }

  /** 汇总实物状态数量供详情与审核页面展示。 */
  public SnSummaryVO summaryDto(String sid) {
    SnSummaryVO snSummaryVO =
        new SnSummaryVO()
            .total(0)
            .pendingLabel(0)
            .idle(0)
            .locked(0)
            .renting(0)
            .repairing(0)
            .lost(0)
            .scrapped(0)
            .retired(0);
    int total = 0;
    for (PropInstanceCountProjection propInstanceCountProjection :
        propSnInstanceMapper.selectPropSnStatusCountBySkuId(sid)) {
      int n = propInstanceCountProjection.getAmount();
      switch (propInstanceCountProjection.getInstanceStatus()) {
        case "pending_label" -> snSummaryVO.setPendingLabel(n);
        case "idle" -> snSummaryVO.setIdle(n);
        case "locked" -> snSummaryVO.setLocked(n);
        case "renting" -> snSummaryVO.setRenting(n);
        case "repairing" -> snSummaryVO.setRepairing(n);
        case "lost" -> snSummaryVO.setLost(n);
        case "scrapped" -> snSummaryVO.setScrapped(n);
        case "retired" -> snSummaryVO.setRetired(n);
        default -> throw PropAccess.inconsistent();
      }
      if (Set.of("idle", "locked", "renting", "repairing")
          .contains(propInstanceCountProjection.getInstanceStatus()))
        total = Math.addExact(total, n);
    }
    snSummaryVO.setTotal(total);
    return snSummaryVO;
  }

  /** 校验实物道具访问权限后返回状态汇总。 */
  public SnSummaryVO summary(String actor, String id, boolean admin) {
    PropSkuDO propSkuDO;
    if (admin) {
      propAccess.admin(actor, "prop-asset", "read");
      propSkuDO = propAccess.sku(id);
    } else {
      propSkuDO = propAccess.own(actor, id);
    }
    if (!propSkuDO.getAssetType().equals(PropAssetType.A_ASSET.name())) throw missing();

    return summaryDto(id);
  }

  /** 分页查询实物资产并补齐二维码和可执行操作。 */
  public PageResult<SnVO> instances(
      String actor, String id, InstanceQueryDTO instanceQueryDTO, boolean admin) {
    PropSkuDO propSkuDO;
    if (admin) {
      propAccess.admin(actor, "prop-asset", "read");
      propSkuDO = propAccess.sku(id);
    } else {
      propSkuDO = propAccess.own(actor, id);
    }
    if (!propSkuDO.getAssetType().equals(PropAssetType.A_ASSET.name())) throw missing();
    boolean business = false;
    if (!admin) {
      try {
        propAccess.business(actor, propSkuDO);
        business = true;
      } catch (ApiFailure apiFailure) {
        if (apiFailure.status >= 500) throw apiFailure;
      }
    }
    final boolean factoryWritable = business;
    instanceQueryDTO.setSkuId(id);
    long total = propSnInstanceMapper.selectPropSnInstanceCountByQuery(instanceQueryDTO);
    instanceQueryDTO.setOffset(offset(instanceQueryDTO));
    instanceQueryDTO.setLimit(number(instanceQueryDTO, "pageSize", 20));
    List<PropSnInstanceDO> propSnInstanceDOList =
        instanceQueryDTO.getOffset() >= total
            ? List.of()
            : propSnInstanceMapper.selectPropSnInstancePageByQuery(instanceQueryDTO);

    List<String> snIds = new ArrayList<>();
    for (PropSnInstanceDO propSnInstanceDO : propSnInstanceDOList)
      snIds.add(propSnInstanceDO.getId());
    Map<String, Chain> chainMap = propQrLookup.find(PropQrTargetType.SN.name(), snIds);
    Map<String, String> pendingAuditIds = new HashMap<>();
    if (!snIds.isEmpty()) {
      for (PropSnPendingProjection propSnPendingProjection :
          propSnAdjustmentAuditMapper.selectPendingSnAdjustmentListBySnIds(snIds))
        pendingAuditIds.putIfAbsent(
            propSnPendingProjection.snId(), propSnPendingProjection.auditId());
    }
    List<SnVO> snVOList = new ArrayList<>();
    for (PropSnInstanceDO propSnInstanceDO : propSnInstanceDOList) {
      String pendingAuditId = pendingAuditIds.get(propSnInstanceDO.getId());
      boolean can = factoryWritable && pendingAuditId == null,
          idle = propSnInstanceDO.getInstanceStatus().equals("idle"),
          repair = propSnInstanceDO.getInstanceStatus().equals("repairing");
      Chain chain = chainMap.get(propSnInstanceDO.getId());
      PropQrCodeDO propQrCodeDO = chain == null ? null : chain.current();
      if (chain != null && !id.equals(chain.latest().getSkuId())) throw PropAccess.inconsistent();
      snVOList.add(
          new SnVO()
              .id(propSnInstanceDO.getId())
              .snCode(propSnInstanceDO.getSnCode())
              .skuId(id)
              .instanceStatus(propSnInstanceDO.getInstanceStatus())
              .version(propSnInstanceDO.getVersion())
              .qrCodeId(propSnInstanceDO.getQrCodeId())
              .currentQrId(propQrCodeDO == null ? null : propQrCodeDO.getId())
              .latestQrId(chain == null ? null : chain.latest().getId())
              .remark(propSnInstanceDO.getRemark())
              .statusChangedAt(time(propSnInstanceDO.getStatusChangedAt()))
              .pendingAdjustmentId(pendingAuditId == null ? null : pendingAuditId)
              .canSelectForDecrease(can && idle)
              .canSelectForRepair(can && idle)
              .canSelectForAbnormal(can && (idle || repair))
              .canCompleteRepair(can && repair)
              .canConfirmLabel(
                  can
                      && propSnInstanceDO.getInstanceStatus().equals("pending_label")
                      && propQrCodeDO != null
                      && "bound".equals(propQrCodeDO.getStatus())
                      && "ready".equals(propQrCodeDO.getImageGenerationStatus()))
              .currentOrderId(propSnInstanceDO.getCurrentOrderId())
              .statusRemark(propSnInstanceDO.getStatusRemark())
              .updatedAt(time(propSnInstanceDO.getUpdatedAt())));
    }
    return page(snVOList, total, instanceQueryDTO);
  }

  /** 按时间及来源条件查询库存变更流水。 */
  public PageResult<MovementVO> movements(
      String actor, String sid, MovementQueryDTO movementQueryDTO, boolean admin) {
    PropSkuDO propSkuDO;
    if (admin) {
      propAccess.admin(actor, "prop-asset", "read");
      propSkuDO = propAccess.sku(sid);
    } else {
      propSkuDO = propAccess.own(actor, sid);
    }
    if (!propSkuDO.getAssetType().equals(PropAssetType.B_CONSUMABLE.name())) throw missing();
    movementQueryDTO.setSkuId(sid);
    java.time.LocalDate
        start =
            (movementQueryDTO.getStartDate() != null)
                ? java.time.LocalDate.parse(movementQueryDTO.getStartDate())
                : null,
        end =
            (movementQueryDTO.getEndDate() != null)
                ? java.time.LocalDate.parse(movementQueryDTO.getEndDate())
                : null;
    if (start != null && end != null && start.isAfter(end)) throw ApiFailure.invalid();
    if (start != null) {
      movementQueryDTO.setStart(start.atStartOfDay());
    }
    if (end != null) {
      movementQueryDTO.setEnd(end.plusDays(1).atStartOfDay());
    }
    long total = propStockMovementMapper.selectPropStockMovementCountByQuery(movementQueryDTO);
    movementQueryDTO.setOffset(offset(movementQueryDTO));
    movementQueryDTO.setLimit(number(movementQueryDTO, "pageSize", 20));
    List<com.mujian.backend.prop.domain.PropStockMovementDO> propStockMovementDOList =
        movementQueryDTO.getOffset() >= total
            ? List.of()
            : propStockMovementMapper.selectPropStockMovementPageByQuery(movementQueryDTO);
    List<MovementVO> movementVOList = new ArrayList<>();
    for (com.mujian.backend.prop.domain.PropStockMovementDO propStockMovementDO :
        propStockMovementDOList) {
      movementVOList.add(
          new MovementVO()
              .id(propStockMovementDO.getId())
              .skuId(sid)
              .beforeQuantity(propStockMovementDO.getBeforeQuantity())
              .afterQuantity(propStockMovementDO.getAfterQuantity())
              .movementType(propStockMovementDO.getMovementType())
              .sourceType(propStockMovementDO.getSourceType())
              .quantity(propStockMovementDO.getQuantity())
              .before(
                  com.mujian.backend.prop.converter.PropReviewConverter.stockSnapshot(
                      propStockMovementDO.getBeforeStockSnapshotJson() == null
                          ? null
                          : propJson.objectMapper.readValue(
                              propStockMovementDO.getBeforeStockSnapshotJson(),
                              com.mujian.backend.prop.dto.PropStockSnapshotDTO.class)))
              .after(
                  com.mujian.backend.prop.converter.PropReviewConverter.stockSnapshot(
                      propStockMovementDO.getAfterStockSnapshotJson() == null
                          ? null
                          : propJson.objectMapper.readValue(
                              propStockMovementDO.getAfterStockSnapshotJson(),
                              com.mujian.backend.prop.dto.PropStockSnapshotDTO.class)))
              .relatedAuditId(propStockMovementDO.getRelatedAuditId())
              .relatedOrderId(propStockMovementDO.getRelatedOrderId())
              .relatedReservationId(propStockMovementDO.getRelatedReservationId())
              .relatedArbitrationId(propStockMovementDO.getRelatedArbitrationId())
              .operatorUserId(propStockMovementDO.getOperatorUserId())
              .operatorRole(propStockMovementDO.getOperatorRole())
              .remark(propStockMovementDO.getRemark())
              .createdAt(time(propStockMovementDO.getCreatedAt())));
    }
    return page(movementVOList, total, movementQueryDTO);
  }

  /** 核对实物归属后查询状态变更历史。 */
  public PageResult<SnLogVO> logs(String actor, String sn, LogQueryDTO logQueryDTO) {
    PropSnInstanceDO propSnInstanceDO = propInventory.sn(sn, false);
    propAccess.own(actor, propSnInstanceDO.getSkuId());
    logQueryDTO.setSnId(sn);
    long total = propInstanceStatusLogMapper.selectPropInstanceStatusLogCountByQuery(logQueryDTO);
    logQueryDTO.setOffset(offset(logQueryDTO));
    logQueryDTO.setLimit(number(logQueryDTO, "pageSize", 20));
    List<com.mujian.backend.prop.domain.PropInstanceStatusLogDO> propInstanceStatusLogDOList =
        logQueryDTO.getOffset() >= total
            ? List.of()
            : propInstanceStatusLogMapper.selectPropInstanceStatusLogPageByQuery(logQueryDTO);
    List<SnLogVO> snLogVOList = new ArrayList<>();
    for (com.mujian.backend.prop.domain.PropInstanceStatusLogDO propInstanceStatusLogDO :
        propInstanceStatusLogDOList) {
      snLogVOList.add(
          new SnLogVO()
              .id(propInstanceStatusLogDO.getId())
              .snId(sn)
              .fromStatus(propInstanceStatusLogDO.getFromStatus())
              .toStatus(propInstanceStatusLogDO.getToStatus())
              .sourceType(propInstanceStatusLogDO.getSourceType())
              .operatorUserId(propInstanceStatusLogDO.getOperatorUserId())
              .operatorRole(propInstanceStatusLogDO.getOperatorRole())
              .relatedAdjustmentId(propInstanceStatusLogDO.getRelatedAdjustmentAuditId())
              .relatedOrderId(propInstanceStatusLogDO.getRelatedOrderId())
              .relatedArbitrationId(propInstanceStatusLogDO.getRelatedArbitrationId())
              .reason(propInstanceStatusLogDO.getReason())
              .createdAt(time(propInstanceStatusLogDO.getCreatedAt())));
    }
    return page(snLogVOList, total, logQueryDTO);
  }

  /** 核对访问权限并计算指定道具的租期可租结果。 */
  public AvailabilityVO availability(
      String actor, String sid, PropRentalPeriodQueryDTO propRentalPeriodQueryDTO) {
    propAccess.admin(actor, "prop-asset", "read");
    PropSkuDO propSkuDO = propAccess.sku(sid);
    Dates dates =
        propAvailability.dates(
            propRentalPeriodQueryDTO.getStartDate(), propRentalPeriodQueryDTO.getEndDate(), false);
    if (dates == null) throw ApiFailure.invalid();
    return propAvailability.result(
        propSkuDO,
        dates,
        propAvailability.quantities(List.of(sid), dates).getOrDefault(sid, 0),
        "online".equals(propSkuDO.getSkuStatus())
            && propAccess.pending(propSkuDO.getId()) == null
            && CODE_ACTIVE.equals(
                propAccess.factoryById(propSkuDO.getFactoryId(), false).getFactoryStatus()));
  }

  /** 校验道具与实物关联后查询库存占用记录。 */
  public PageResult<ReservationVO> reservations(
      String actor, ReservationQueryDTO reservationQueryDTO) {
    propAccess.admin(actor, "prop-asset", "read");
    if ((reservationQueryDTO.getSkuId() != null)) propAccess.sku(reservationQueryDTO.getSkuId());
    if ((reservationQueryDTO.getSnId() != null)) {
      PropSnInstanceDO propSnInstanceDO = propInventory.sn(reservationQueryDTO.getSnId(), false);
      if ((reservationQueryDTO.getSkuId() != null)
          && !reservationQueryDTO.getSkuId().equals(propSnInstanceDO.getSkuId())) throw missing();
    }
    Dates dates =
        propAvailability.dates(
            reservationQueryDTO.getStartDate(), reservationQueryDTO.getEndDate(), true);
    if (dates != null) {
      reservationQueryDTO.setStart(dates.start());
      reservationQueryDTO.setEnd(dates.end());
    }
    if ((reservationQueryDTO.getKeyword() != null)) {
      reservationQueryDTO.setKeyword(text(reservationQueryDTO.getKeyword(), 128));
    }
    long total =
        inventoryReservationMapper.selectInventoryReservationCountByQuery(reservationQueryDTO);
    reservationQueryDTO.setOffset(offset(reservationQueryDTO));
    reservationQueryDTO.setLimit(number(reservationQueryDTO, "pageSize", 20));
    List<com.mujian.backend.prop.projection.PropReservationProjection>
        propReservationProjectionList =
            reservationQueryDTO.getOffset() >= total
                ? List.of()
                : inventoryReservationMapper.selectInventoryReservationPageByQuery(
                    reservationQueryDTO);
    List<ReservationVO> reservationVOList = new ArrayList<>();
    for (com.mujian.backend.prop.projection.PropReservationProjection propReservationProjection :
        propReservationProjectionList) {
      reservationVOList.add(
          new ReservationVO()
              .id(propReservationProjection.getId())
              .skuId(propReservationProjection.getSkuId())
              .skuCode(propReservationProjection.getSkuCode())
              .propName(propReservationProjection.getPropName())
              .snCode(propReservationProjection.getSnCode())
              .checkoutBatchId(propReservationProjection.getCheckoutBatchId())
              .checkoutBatchItemSnapshotId(
                  propReservationProjection.getCheckoutBatchItemSnapshotId())
              .rentalOrderItemId(propReservationProjection.getRentalOrderItemId())
              .updatedAt(time(propReservationProjection.getUpdatedAt()))
              .snId(propReservationProjection.getSnId())
              .quantity(propReservationProjection.getQuantity())
              .rentalStartDate(propReservationProjection.getRentalStartDate().toString())
              .rentalEndDate(propReservationProjection.getRentalEndDate().toString())
              .occupyStartDate(propReservationProjection.getOccupyStartDate().toString())
              .occupyEndDate(propReservationProjection.getOccupyEndDate().toString())
              .reservationStatus(propReservationProjection.getReservationStatus())
              .rentalOrderId(propReservationProjection.getRentalOrderId())
              .sourceType(propReservationProjection.getSourceType())
              .sourceId(propReservationProjection.getSourceId())
              .expiresAt(time(propReservationProjection.getExpiresAt())));
    }
    return page(reservationVOList, total, reservationQueryDTO);
  }

  /** 核对工厂及可选道具归属后返回编辑选项。 */
  public com.mujian.backend.prop.vo.OptionsVO ownedOptions(String actor, String skuId) {
    propAccess.factory(actor, false, false);
    if (skuId != null) propAccess.own(actor, skuId);
    return propCatalogService.options();
  }
}
