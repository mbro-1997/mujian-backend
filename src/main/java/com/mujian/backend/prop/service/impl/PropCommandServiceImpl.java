package com.mujian.backend.prop.service.impl;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.PropReviewSources.*;
import static com.mujian.backend.infrastructure.persistence.MappingInputs.first;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.PropAssetType;
import com.mujian.backend.common.enums.PropReasonCode;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.prop.domain.PropSkuAuditDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSkuStockDO;
import com.mujian.backend.prop.domain.PropSnAdjustmentAuditDO;
import com.mujian.backend.prop.domain.PropSnAdjustmentItemDO;
import com.mujian.backend.prop.domain.PropSnInstanceDO;
import com.mujian.backend.prop.domain.PropStockAdjustmentAuditDO;
import com.mujian.backend.prop.dto.CreateSkuDTO;
import com.mujian.backend.prop.dto.EditOptionsDTO;
import com.mujian.backend.prop.dto.PropAuditFactsDTO.SkuOptions;
import com.mujian.backend.prop.dto.PropSkuFieldsDTO;
import com.mujian.backend.prop.dto.PropWriteDTO.CancelReview;
import com.mujian.backend.prop.dto.ResubmitSkuDTO;
import com.mujian.backend.prop.dto.SnAdjustmentDTO;
import com.mujian.backend.prop.dto.StatusApplicationDTO;
import com.mujian.backend.prop.dto.StockAdjustmentDTO;
import com.mujian.backend.prop.dto.VersionReasonDTO;
import com.mujian.backend.prop.mapper.PropSkuAuditMapper;
import com.mujian.backend.prop.mapper.PropSkuMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentAuditMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentItemMapper;
import com.mujian.backend.prop.mapper.PropStockAdjustmentAuditMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.projection.PropFactoryProjection;
import com.mujian.backend.prop.projection.PropReviewProjection;
import com.mujian.backend.prop.service.PropCasework;
import com.mujian.backend.prop.service.PropCatalogService;
import com.mujian.backend.prop.service.PropCommandService;
import com.mujian.backend.prop.service.PropInventory;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.service.PropRequests;
import com.mujian.backend.prop.service.PropSnapshots;
import com.mujian.backend.prop.utils.PropJson;
import com.mujian.backend.prop.vo.CommandResultVO;
import java.time.Clock;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropCommandServiceImpl implements PropCommandService {
  private final PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper;
  private final PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper;
  private final PropSkuAuditMapper propSkuAuditMapper;
  private final PropSkuMapper propSkuMapper;
  private final PropSnAdjustmentItemMapper propSnAdjustmentItemMapper;
  private final PropAccess propAccess;
  private final PropRequests propRequests;
  private final PropSnapshots propSnapshots;
  private final PropInventory propInventory;
  private final PropCatalogService propCatalogService;
  private final PropCasework propCasework;
  private final PropJson propJson;
  private final Clock clock;
  private final PropMaterialService propMaterialService;
  private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropCommandServiceImpl(
      PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper,
      PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper,
      PropSkuAuditMapper propSkuAuditMapper,
      PropSkuMapper propSkuMapper,
      PropSnAdjustmentItemMapper propSnAdjustmentItemMapper,
      PropAccess propAccess,
      PropRequests propRequests,
      PropSnapshots propSnapshots,
      PropInventory propInventory,
      PropCatalogService propCatalogService,
      PropCasework propCasework,
      PropJson propJson,
      Clock clock,
      PropMaterialService propMaterialService,
      org.springframework.transaction.PlatformTransactionManager platformTransactionManager) {
    this.propMaterialService = propMaterialService;
    transactionTemplate =
        new org.springframework.transaction.support.TransactionTemplate(platformTransactionManager);
    transactionTemplate.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.propStockAdjustmentAuditMapper = propStockAdjustmentAuditMapper;
    this.propSnAdjustmentAuditMapper = propSnAdjustmentAuditMapper;
    this.propSkuAuditMapper = propSkuAuditMapper;
    this.propSkuMapper = propSkuMapper;
    this.propSnAdjustmentItemMapper = propSnAdjustmentItemMapper;
    this.propAccess = propAccess;
    this.propRequests = propRequests;
    this.propSnapshots = propSnapshots;
    this.propInventory = propInventory;
    this.propCatalogService = propCatalogService;
    this.propCasework = propCasework;
    this.propJson = propJson;
    this.clock = clock;
  }

  /** 校验防重和材料后，在同一事务中创建道具及首次上架申请。 */
  public CommandResultVO create(String actor, String key, CreateSkuDTO createSkuDTO) {
    propAccess.factory(actor, false, false);
    CommandResultVO commandResultVO =
        propRequests.replay(actor, key, propJson.hash("create_sku", null, createSkuDTO));
    if (commandResultVO != null) return commandResultVO;
    propMaterialService.preflight(actor, createSkuDTO.fields().images(), "prop_image");
    return transactionTemplate.execute(
        transactionStatus -> {
          propAccess.factory(actor, false, false);
          propAccess.factoryAccess.lock(actor);
          PropFactoryProjection propFactoryProjection = propAccess.factory(actor, false, true);
          String hash = propJson.hash("create_sku", null, createSkuDTO);
          CommandResultVO commandResultVOLockedReplay = propRequests.replay(actor, key, hash);
          if (commandResultVOLockedReplay != null) return commandResultVOLockedReplay;
          propAccess.factory(actor, true, false);
          PropSkuFieldsDTO propSkuFieldsDTO = propSnapshots.build(actor, createSkuDTO.fields());
          String id = Ids.next();
          PropSkuDO propSkuDO =
              propSnapshots
                  .skuFields(propSkuFieldsDTO)
                  .id(id)
                  .factoryId(propFactoryProjection.getId())
                  .skuCode("SKU-" + id)
                  .assetType(createSkuDTO.assetType())
                  .skuStatus("draft")
                  .fillStatus("filled")
                  .version(0)
                  .createdAt(now(clock))
                  .updatedAt(now(clock));
          propSkuMapper.insertPropSku(propSkuDO);
          propSnapshots.writeFields(id, propSkuFieldsDTO);
          PropSkuDO propSkuDOApplicationSku = propAccess.sku(id);
          PropSkuFieldsDTO propSkuFieldsDTOApplicationFields = propSkuFieldsDTO;
          Integer applicationQuantity = createSkuDTO.initialQuantity();
          String previousAuditId = null;
          String applicationAction = "create";
          String applicationRemark = createSkuDTO.applyRemark();

          PropSkuAuditDO propSkuAuditDO =
              new PropSkuAuditDO()
                  .id(Ids.next())
                  .skuId(propSkuDOApplicationSku.getId())
                  .applicantUserId(actor)
                  .auditStatus(CODE_PENDING)
                  .requestId(id(key))
                  .requestHash(hash)
                  .submittedAt(now(clock))
                  .version(0)
                  .createdAt(now(clock))
                  .updatedAt(now(clock));
          boolean create = applicationAction.equals("create");
          propSkuAuditDO
              .actionType(applicationAction)
              .beforeSkuStatus(propSkuDOApplicationSku.getSkuStatus())
              .targetSkuStatus(applicationAction.equals("down") ? "offline" : "online")
              .beforeSnapshotJson(
                  create
                      ? null
                      : propJson.write(
                          propSnapshots.snapshot(
                              propSkuDOApplicationSku,
                              propSnapshots.current(propSkuDOApplicationSku),
                              null,
                              null)))
              .proposedSnapshotJson(
                  propJson.write(
                      propSnapshots.snapshot(
                          propSkuDOApplicationSku,
                          propSkuFieldsDTOApplicationFields,
                          applicationQuantity,
                          previousAuditId)))
              .initialSnQuantity(
                  create
                          && propSkuDOApplicationSku
                              .getAssetType()
                              .equals(PropAssetType.A_ASSET.name())
                      ? applicationQuantity
                      : null)
              .initialStockQuantity(
                  create
                          && propSkuDOApplicationSku
                              .getAssetType()
                              .equals(PropAssetType.B_CONSUMABLE.name())
                      ? applicationQuantity
                      : null)
              .applyRemark(applicationRemark)
              .previousAuditId(previousAuditId)
              .baseSkuVersion(propSkuDOApplicationSku.getVersion());
          propSkuAuditMapper.insertPropSkuAudit(propSkuAuditDO);
          String submittedSourceId = propSkuAuditDO.getId();

          PropReviewProjection propReviewProjection =
              propAccess.review(SKU_AUDIT, submittedSourceId, false);
          propCasework.defer(SKU_AUDIT, submittedSourceId);
          return propRequests.submitted(SKU_AUDIT, propReviewProjection, "submitted");
        });
  }

  /** 在原申请事务中提交资料更新，批准前不改生效资料及库存。 */
  public CommandResultVO change(
      String actor,
      String skuId,
      String key,
      com.mujian.backend.prop.dto.SkuChangeDTO skuChangeDTO) {
    propAccess.own(actor, skuId);
    String hash = propJson.hash("update_sku", skuId, skuChangeDTO);
    CommandResultVO commandResultVO = propRequests.replay(actor, key, hash);
    if (commandResultVO != null) return commandResultVO;
    try {
      propMaterialService.preflight(actor, skuChangeDTO.fields().images(), "prop_image");
    } catch (ApiFailure apiFailure) {
      if (Set.of(
              D2Codes.CODE_MATERIAL_REFERENCE_INVALID,
              D2Codes.CODE_MATERIAL_REFERENCE_EXPIRED,
              D2Codes.CODE_RESOURCE_NOT_FOUND,
              D2Codes.CODE_REQUEST_INVALID)
          .contains(apiFailure.reason)) throw conflict(PropReasonCode.PROP_IMAGE_INVALID.name());
      throw apiFailure;
    }
    return transactionTemplate.execute(
        transactionStatus -> {
          PropSkuDO propSkuDO = propAccess.lock(actor, skuId, false);
          CommandResultVO commandResultVOReplay = propRequests.replay(actor, key, hash);
          if (commandResultVOReplay != null) return commandResultVOReplay;
          propAccess.changeAllowed(actor, propSkuDO);
          propAccess.version(propSkuDO.getVersion(), skuChangeDTO.expectedSkuVersion());
          if (skuChangeDTO.previousApplicationId() != null) {
            PropSkuAuditDO previous =
                propSkuAuditMapper.selectPropSkuAudit(
                    id(skuChangeDTO.previousApplicationId()), true);
            if (previous == null
                || !skuId.equals(previous.getSkuId())
                || !actor.equals(previous.getApplicantUserId())) throw missing();
            if (!"update".equals(previous.getActionType())
                || !Set.of("rejected", "cancelled").contains(previous.getAuditStatus()))
              throw conflict(PropReasonCode.SKU_CHANGE_NOT_ALLOWED.name());
          }
          com.mujian.backend.prop.dto.SkuChangeDTO.Fields fields = skuChangeDTO.fields();
          // 免审选项从已锁定当前资料继承，客户端不能借本入口修改。
          com.mujian.backend.prop.dto.SkuInputDTO skuInputDTO =
              new com.mujian.backend.prop.dto.SkuInputDTO(
                  fields.propName(),
                  propSkuDO.getTypeCode(),
                  propSnapshots.styles(skuId),
                  fields.sizeDesc(),
                  fields.lengthCm(),
                  fields.widthCm(),
                  fields.heightCm(),
                  fields.materialDesc(),
                  fields.fireResistantCode(),
                  fields.weightKg(),
                  propSkuDO.getTransportSuggestion(),
                  fields.dailyRentPriceFen(),
                  fields.depositAmountFen(),
                  fields.images(),
                  fields.remark(),
                  fields.packagingFactorBps());
          PropSkuFieldsDTO propSkuFieldsDTO =
              propSnapshots.buildChange(actor, skuInputDTO, propSkuDO);
          PropSkuAuditDO propSkuAuditDO =
              new PropSkuAuditDO()
                  .id(Ids.next())
                  .skuId(skuId)
                  .applicantUserId(actor)
                  .auditStatus(CODE_PENDING)
                  .requestId(id(key))
                  .requestHash(hash)
                  .submittedAt(now(clock))
                  .version(0)
                  .createdAt(now(clock))
                  .updatedAt(now(clock))
                  .actionType("update")
                  .beforeSkuStatus(propSkuDO.getSkuStatus())
                  .targetSkuStatus(propSkuDO.getSkuStatus())
                  .beforeSnapshotJson(
                      propJson.write(
                          propSnapshots.snapshot(
                              propSkuDO, propSnapshots.current(propSkuDO), null, null)))
                  .proposedSnapshotJson(
                      propJson.write(
                          propSnapshots.snapshot(
                              propSkuDO,
                              propSkuFieldsDTO,
                              null,
                              skuChangeDTO.previousApplicationId())))
                  .initialSnQuantity(null)
                  .initialStockQuantity(null)
                  .applyRemark(text(skuChangeDTO.applyRemark(), 255))
                  .previousAuditId(skuChangeDTO.previousApplicationId())
                  .baseSkuVersion(propSkuDO.getVersion());
          propSkuAuditMapper.insertPropSkuAudit(propSkuAuditDO);
          propCasework.defer(SKU_AUDIT, propSkuAuditDO.getId());
          return propRequests.submitted(
              SKU_AUDIT, propAccess.review(SKU_AUDIT, propSkuAuditDO.getId(), false), "submitted");
        });
  }

  /** 恢复或重新提交被驳回、已撤回的道具申请。 */
  public CommandResultVO resubmit(
      String actor, String skuId, String key, ResubmitSkuDTO resubmitSkuDTO) {
    propAccess.own(actor, skuId);
    CommandResultVO commandResultVO =
        propRequests.replay(actor, key, propJson.hash("resubmit_sku", skuId, resubmitSkuDTO));
    if (commandResultVO != null) return commandResultVO;
    propMaterialService.preflight(actor, resubmitSkuDTO.fields().images(), "prop_image");
    return transactionTemplate.execute(
        transactionStatus -> {
          propAccess.own(actor, skuId);
          PropSkuDO propSkuDO = propAccess.lock(actor, skuId, false);
          String hash = propJson.hash("resubmit_sku", skuId, resubmitSkuDTO);
          CommandResultVO commandResultVOLockedReplay = propRequests.replay(actor, key, hash);
          if (commandResultVOLockedReplay != null) return commandResultVOLockedReplay;
          propAccess.factory(actor, true, false);
          PropSkuAuditDO propSkuAuditDO = propAccess.pending(propSkuDO.getId());
          CommandResultVO commandResultVOPending =
              propSkuAuditDO == null
                  ? null
                  : propRequests.submitted(SKU_AUDIT, propSkuAuditDO, "existing_pending");
          if (commandResultVOPending != null) return commandResultVOPending;
          propAccess.version(propSkuDO.getVersion(), resubmitSkuDTO.expectedSkuVersion());
          PropSkuAuditDO propSkuAuditDOPrior =
              propSkuAuditMapper.selectPropSkuAudit(
                  id(resubmitSkuDTO.previousApplicationId()), true);
          if (propSkuAuditDOPrior == null
              || !skuId.equals(propSkuAuditDOPrior.getSkuId())
              || !actor.equals(propSkuAuditDOPrior.getApplicantUserId())) throw missing();
          if (!"draft".equals(propSkuDO.getSkuStatus())
              || !"create".equals(propSkuAuditDOPrior.getActionType())
              || !Set.of(CODE_REJECTED, "cancelled").contains(propSkuAuditDOPrior.getAuditStatus()))
            throw conflict(PropReasonCode.SKU_STATE_CONFLICT.name());
          PropSkuAuditDO propSkuAuditDOLatest =
              first(propSkuAuditMapper.selectLatestPropSkuAuditListBySkuId(skuId));
          if (!propSkuAuditDOPrior.getId().equals(propSkuAuditDOLatest.getId()))
            throw conflict(CODE_VERSION_CONFLICT);
          PropSkuDO propSkuDOApplicationSku = propSkuDO;
          PropSkuFieldsDTO propSkuFieldsDTO = propSnapshots.build(actor, resubmitSkuDTO.fields());
          Integer applicationQuantity = resubmitSkuDTO.initialQuantity();
          String previousAuditId = propSkuAuditDOPrior.getId();
          String applicationAction = "create";
          String applicationRemark = resubmitSkuDTO.applyRemark();

          PropSkuAuditDO propSkuAuditDOResubmitted =
              new PropSkuAuditDO()
                  .id(Ids.next())
                  .skuId(propSkuDOApplicationSku.getId())
                  .applicantUserId(actor)
                  .auditStatus(CODE_PENDING)
                  .requestId(id(key))
                  .requestHash(hash)
                  .submittedAt(now(clock))
                  .version(0)
                  .createdAt(now(clock))
                  .updatedAt(now(clock));
          boolean create = applicationAction.equals("create");
          propSkuAuditDOResubmitted
              .actionType(applicationAction)
              .beforeSkuStatus(propSkuDOApplicationSku.getSkuStatus())
              .targetSkuStatus(applicationAction.equals("down") ? "offline" : "online")
              .beforeSnapshotJson(
                  create
                      ? null
                      : propJson.write(
                          propSnapshots.snapshot(
                              propSkuDOApplicationSku,
                              propSnapshots.current(propSkuDOApplicationSku),
                              null,
                              null)))
              .proposedSnapshotJson(
                  propJson.write(
                      propSnapshots.snapshot(
                          propSkuDOApplicationSku,
                          propSkuFieldsDTO,
                          applicationQuantity,
                          previousAuditId)))
              .initialSnQuantity(
                  create
                          && propSkuDOApplicationSku
                              .getAssetType()
                              .equals(PropAssetType.A_ASSET.name())
                      ? applicationQuantity
                      : null)
              .initialStockQuantity(
                  create
                          && propSkuDOApplicationSku
                              .getAssetType()
                              .equals(PropAssetType.B_CONSUMABLE.name())
                      ? applicationQuantity
                      : null)
              .applyRemark(applicationRemark)
              .previousAuditId(previousAuditId)
              .baseSkuVersion(propSkuDOApplicationSku.getVersion());
          propSkuAuditMapper.insertPropSkuAudit(propSkuAuditDOResubmitted);
          String submittedSourceId = propSkuAuditDOResubmitted.getId();

          PropReviewProjection propReviewProjection =
              propAccess.review(SKU_AUDIT, submittedSourceId, false);
          propCasework.defer(SKU_AUDIT, submittedSourceId);
          return propRequests.submitted(SKU_AUDIT, propReviewProjection, "submitted");
        });
  }

  /** 按版本修改道具分类、风格及运输建议，并记录幂等结果。 */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public CommandResultVO options(
      String actor, String sid, String key, EditOptionsDTO editOptionsDTO) {
    propAccess.own(actor, sid);
    PropSkuDO propSkuDO = propAccess.lock(actor, sid, false);
    String hash = propJson.hash("edit_sku_options", sid, editOptionsDTO);
    CommandResultVO commandResultVO = propRequests.replay(actor, key, hash);
    if (commandResultVO != null) return commandResultVO;
    propAccess.business(actor, propSkuDO);
    propAccess.version(propSkuDO.getVersion(), editOptionsDTO.expectedSkuVersion());
    List<String> oldStyles = propSnapshots.styles(sid);
    propCatalogService.validateSelection(
        editOptionsDTO.typeCode(),
        null,
        editOptionsDTO.styleCodes(),
        List.of(propSkuDO.getTypeCode()),
        oldStyles);
    SkuOptions skuOptions =
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.SkuOptions(
            propSkuDO.getTypeCode(),
            oldStyles,
            propSkuDO.getTransportSuggestion(),
            propSkuDO.getVersion());
    boolean changed =
        !editOptionsDTO.typeCode().equals(propSkuDO.getTypeCode())
            || !oldStyles.equals(editOptionsDTO.styleCodes())
            || !text(editOptionsDTO.transportSuggestion(), 255)
                .equals(propSkuDO.getTransportSuggestion());
    if (changed) {
      propSkuMapper.updatePropSkuOptionsByVersion(
          new com.mujian.backend.prop.dto.PropWriteDTO.UpdateSkuOptions(
              sid,
              editOptionsDTO.typeCode(),
              text(editOptionsDTO.transportSuggestion(), 255),
              Math.addExact(propSkuDO.getVersion(), 1),
              now(clock)));
      propSnapshots.writeStyles(sid, editOptionsDTO.styleCodes());
    }
    PropSkuDO propSkuDOUpdated = propAccess.sku(sid);
    CommandResultVO commandResultVOApplied =
        propRequests.result(
            "edit_sku_options",
            "applied",
            sid,
            "sku",
            sid,
            propSkuDOUpdated.getVersion(),
            propSkuDOUpdated.getSkuStatus(),
            now(clock));
    propRequests.record(
        actor,
        key,
        hash,
        "edit_sku_options",
        commandResultVOApplied,
        skuOptions,
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.SkuOptions(
            propSkuDOUpdated.getTypeCode(),
            propSnapshots.styles(sid),
            propSkuDOUpdated.getTransportSuggestion(),
            propSkuDOUpdated.getVersion()));
    return commandResultVOApplied;
  }

  /** 校验道具版本与状态后提交上下架申请。 */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public CommandResultVO status(
      String actor, String sid, String key, StatusApplicationDTO statusApplicationDTO) {
    propAccess.own(actor, sid);
    PropSkuDO propSkuDO = propAccess.lock(actor, sid, false);
    String hash = propJson.hash("change_sku_status", sid, statusApplicationDTO);
    CommandResultVO commandResultVO = propRequests.replay(actor, key, hash);
    if (commandResultVO != null) return commandResultVO;
    propAccess.factory(actor, true, false);
    PropSkuAuditDO propSkuAuditDO = propAccess.pending(propSkuDO.getId());
    CommandResultVO commandResultVOPending =
        propSkuAuditDO == null
            ? null
            : propRequests.submitted(SKU_AUDIT, propSkuAuditDO, "existing_pending");
    if (commandResultVOPending != null) return commandResultVOPending;
    propAccess.version(propSkuDO.getVersion(), statusApplicationDTO.expectedSkuVersion());
    if (!(statusApplicationDTO.actionType().equals("up") ? "offline" : "online")
        .equals(propSkuDO.getSkuStatus())) throw conflict(PropReasonCode.SKU_STATE_CONFLICT.name());
    PropSkuDO propSkuDOApplicationSku = propSkuDO;
    PropSkuFieldsDTO propSkuFieldsDTO = propSnapshots.current(propSkuDO);
    Integer applicationQuantity = null;
    String previousAuditId = null;
    String applicationAction = statusApplicationDTO.actionType();
    String applicationRemark = statusApplicationDTO.applyRemark();

    PropSkuAuditDO propSkuAuditDONew =
        new PropSkuAuditDO()
            .id(Ids.next())
            .skuId(propSkuDOApplicationSku.getId())
            .applicantUserId(actor)
            .auditStatus(CODE_PENDING)
            .requestId(id(key))
            .requestHash(hash)
            .submittedAt(now(clock))
            .version(0)
            .createdAt(now(clock))
            .updatedAt(now(clock));
    boolean create = applicationAction.equals("create");
    propSkuAuditDONew
        .actionType(applicationAction)
        .beforeSkuStatus(propSkuDOApplicationSku.getSkuStatus())
        .targetSkuStatus(applicationAction.equals("down") ? "offline" : "online")
        .beforeSnapshotJson(
            create
                ? null
                : propJson.write(
                    propSnapshots.snapshot(
                        propSkuDOApplicationSku,
                        propSnapshots.current(propSkuDOApplicationSku),
                        null,
                        null)))
        .proposedSnapshotJson(
            propJson.write(
                propSnapshots.snapshot(
                    propSkuDOApplicationSku,
                    propSkuFieldsDTO,
                    applicationQuantity,
                    previousAuditId)))
        .initialSnQuantity(
            create && propSkuDOApplicationSku.getAssetType().equals(PropAssetType.A_ASSET.name())
                ? applicationQuantity
                : null)
        .initialStockQuantity(
            create
                    && propSkuDOApplicationSku
                        .getAssetType()
                        .equals(PropAssetType.B_CONSUMABLE.name())
                ? applicationQuantity
                : null)
        .applyRemark(applicationRemark)
        .previousAuditId(previousAuditId)
        .baseSkuVersion(propSkuDOApplicationSku.getVersion());
    propSkuAuditMapper.insertPropSkuAudit(propSkuAuditDONew);
    String submittedSourceId = propSkuAuditDONew.getId();

    PropReviewProjection propReviewProjection =
        propAccess.review(SKU_AUDIT, submittedSourceId, false);
    propCasework.defer(SKU_AUDIT, submittedSourceId);
    return propRequests.submitted(SKU_AUDIT, propReviewProjection, "submitted");
  }

  /** 锁定实物资产并提交增减、维修或异常扣减申请。 */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public CommandResultVO snAdjustment(
      String actor, String sid, String key, SnAdjustmentDTO snAdjustmentDTO) {
    propAccess.own(actor, sid);
    PropSkuDO propSkuDO = propAccess.lock(actor, sid, false);
    String hash = propJson.hash("create_sn_adjustment", sid, snAdjustmentDTO);
    CommandResultVO commandResultVO = propRequests.replay(actor, key, hash);
    if (commandResultVO != null) return commandResultVO;
    propAccess.business(actor, propSkuDO);
    if (!PropAssetType.A_ASSET.name().equals(propSkuDO.getAssetType()))
      throw conflict(PropReasonCode.SKU_STATE_CONFLICT.name());
    PropSnAdjustmentAuditDO propSnAdjustmentAuditDO =
        new PropSnAdjustmentAuditDO()
            .id(Ids.next())
            .skuId(sid)
            .applicantUserId(actor)
            .auditStatus(CODE_PENDING)
            .requestId(id(key))
            .requestHash(hash)
            .submittedAt(now(clock))
            .version(0)
            .createdAt(now(clock))
            .updatedAt(now(clock));
    boolean increase = snAdjustmentDTO.adjustmentType().equals("increase");
    ArrayList<PropSnInstanceDO> propSnInstanceDOList = new ArrayList<PropSnInstanceDO>();
    if (!increase) {
      for (String snId : new TreeSet<>(snAdjustmentDTO.snIds())) {
        PropSnInstanceDO propSnInstanceDO = propInventory.sn(snId, true);
        if (!sid.equals(propSnInstanceDO.getSkuId())) throw missing();
        if (propInventory.pending(snId) != null)
          throw conflict(PropReasonCode.SN_ADJUSTMENT_PENDING.name());
        if (!"idle".equals(propSnInstanceDO.getInstanceStatus())
            && !(snAdjustmentDTO.adjustmentType().equals("abnormal_decrease")
                && "repairing".equals(propSnInstanceDO.getInstanceStatus())))
          throw conflict(PropReasonCode.SN_STATE_CONFLICT.name());
        propSnInstanceDOList.add(propSnInstanceDO);
      }
    }
    propSnAdjustmentAuditDO
        .factoryId(propSkuDO.getFactoryId())
        .adjustmentType(snAdjustmentDTO.adjustmentType())
        .increaseScene(increase ? "additional" : null)
        .requestedQuantity(increase ? snAdjustmentDTO.quantity() : propSnInstanceDOList.size())
        .applyRemark(text(snAdjustmentDTO.reason(), 255));
    propSnAdjustmentAuditMapper.insertPropSnAdjustmentAudit(propSnAdjustmentAuditDO);
    String auditId = propSnAdjustmentAuditDO.getId();
    for (PropSnInstanceDO propSnInstanceDOLocked : propSnInstanceDOList)
      propSnAdjustmentItemMapper.insertPropSnAdjustmentItem(
          new PropSnAdjustmentItemDO()
              .id(Ids.next())
              .adjustmentAuditId(auditId)
              .skuId(sid)
              .snId(propSnInstanceDOLocked.getId())
              .snCodeSnapshot(propSnInstanceDOLocked.getSnCode())
              .beforeInstanceStatus(propSnInstanceDOLocked.getInstanceStatus())
              .targetInstanceStatus(
                  switch (snAdjustmentDTO.adjustmentType()) {
                    case "decrease" -> "retired";
                    case "repair" -> "repairing";
                    default -> snAdjustmentDTO.abnormalResult();
                  })
              .itemRemark(snAdjustmentDTO.reason())
              .createdAt(now(clock)));
    PropSnAdjustmentAuditDO propSnAdjustmentAuditDOSubmitted =
        propSnAdjustmentAuditMapper.selectPropSnAdjustmentAudit(auditId, false);
    propCasework.defer(SN_ADJUSTMENT, propSnAdjustmentAuditDOSubmitted.getId());
    return propRequests.submitted(SN_ADJUSTMENT, propSnAdjustmentAuditDOSubmitted, "submitted");
  }

  /** 锁定库存台账并提交数量调整申请。 */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public CommandResultVO stockAdjustment(
      String actor, String sid, String key, StockAdjustmentDTO stockAdjustmentDTO) {
    propAccess.own(actor, sid);
    PropSkuDO propSkuDO = propAccess.lock(actor, sid, false);
    String hash = propJson.hash("create_stock_adjustment", sid, stockAdjustmentDTO);
    CommandResultVO commandResultVO = propRequests.replay(actor, key, hash);
    if (commandResultVO != null) return commandResultVO;
    propAccess.business(actor, propSkuDO);
    if (!PropAssetType.B_CONSUMABLE.name().equals(propSkuDO.getAssetType()))
      throw conflict(PropReasonCode.SKU_STATE_CONFLICT.name());
    PropSkuStockDO propSkuStockDO = propInventory.stock(sid, true);
    int delta =
        stockAdjustmentDTO.adjustmentType().equals("increase")
            ? stockAdjustmentDTO.quantity()
            : -stockAdjustmentDTO.quantity();
    propInventory.assertReduction(propSkuStockDO, delta);
    PropStockAdjustmentAuditDO propStockAdjustmentAuditDO =
        new PropStockAdjustmentAuditDO()
            .id(Ids.next())
            .skuId(sid)
            .applicantUserId(actor)
            .auditStatus(CODE_PENDING)
            .requestId(id(key))
            .requestHash(hash)
            .submittedAt(now(clock))
            .version(0)
            .createdAt(now(clock))
            .updatedAt(now(clock));
    propStockAdjustmentAuditDO
        .assetType(PropAssetType.B_CONSUMABLE.name())
        .adjustmentType(stockAdjustmentDTO.adjustmentType())
        .quantityDelta(delta)
        .abnormalResult(stockAdjustmentDTO.abnormalResult())
        .beforeTotalQuantity(propSkuStockDO.getTotalQuantity())
        .beforeAvailableQuantity(propSkuStockDO.getAvailableQuantity())
        .reason(text(stockAdjustmentDTO.reason(), 255));
    propStockAdjustmentAuditMapper.insertPropStockAdjustmentAudit(propStockAdjustmentAuditDO);
    String submittedSourceId = propStockAdjustmentAuditDO.getId();

    PropReviewProjection propReviewProjection =
        propAccess.review(STOCK_ADJUSTMENT, submittedSourceId, false);
    propCasework.defer(STOCK_ADJUSTMENT, submittedSourceId);
    return propRequests.submitted(STOCK_ADJUSTMENT, propReviewProjection, "submitted");
  }

  /** 校验维修状态后恢复资产可用状态并记录流水。 */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public CommandResultVO repair(
      String actor, String snId, String key, VersionReasonDTO versionReasonDTO) {
    PropSnInstanceDO propSnInstanceDO = propInventory.sn(snId, false);
    propAccess.own(actor, propSnInstanceDO.getSkuId());
    PropSkuDO propSkuDO = propAccess.lock(actor, propSnInstanceDO.getSkuId(), false);
    String hash = propJson.hash("repair_complete", snId, versionReasonDTO);
    CommandResultVO commandResultVO = propRequests.replay(actor, key, hash);
    if (commandResultVO != null) return commandResultVO;
    propAccess.business(actor, propSkuDO);
    PropSnInstanceDO propSnInstanceDOLocked = propInventory.sn(snId, true);
    propAccess.version(propSnInstanceDOLocked.getVersion(), versionReasonDTO.expectedVersion());
    if (propInventory.pending(snId) != null)
      throw conflict(PropReasonCode.SN_ADJUSTMENT_PENDING.name());
    if (!"repairing".equals(propSnInstanceDOLocked.getInstanceStatus()))
      throw conflict(PropReasonCode.SN_STATE_CONFLICT.name());
    String operationId = Ids.next();
    propInventory.transition(
        propSnInstanceDOLocked,
        "idle",
        actor,
        FulfillmentRole.factory.name(),
        "repair_complete",
        text(versionReasonDTO.reason(), 255),
        null,
        "prop-op:" + operationId);
    CommandResultVO commandResultVOApplied =
        propRequests.result(
            "repair_complete",
            "applied",
            propSkuDO.getId(),
            "sn",
            snId,
            propSnInstanceDOLocked.getVersion() + 1,
            "idle",
            now(clock));
    propRequests.record(
        operationId,
        actor,
        key,
        hash,
        "repair_complete",
        commandResultVOApplied,
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.SnStatus(
            propSnInstanceDOLocked.getInstanceStatus(),
            propSnInstanceDOLocked.getVersion(),
            propSnInstanceDOLocked.getQrCodeId()),
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.SnStatus(
            "idle", propSnInstanceDOLocked.getVersion() + 1, propSnInstanceDOLocked.getQrCodeId()));
    return commandResultVOApplied;
  }

  /** 按原锁序撤回待审申请并记录业务结果。 */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public CommandResultVO cancel(
      String actor, String type, String auditId, String key, VersionReasonDTO versionReasonDTO) {
    if (type.equals(QR_EXCEPTION)) throw ApiFailure.invalid();
    PropReviewProjection propReviewProjection = propAccess.source(actor, type, auditId, false);
    PropSkuDO propSkuDO = propAccess.lock(actor, propReviewProjection.getSkuId(), false);
    String hash = propJson.hash("cancel_application", type + ":" + auditId, versionReasonDTO);
    CommandResultVO commandResultVO = propRequests.replay(actor, key, hash);
    if (commandResultVO != null) return commandResultVO;
    propAccess.factoryAccess.context(actor, true);
    if (type.equals(SN_ADJUSTMENT)) propInventory.lockItems(auditId);
    if (type.equals(STOCK_ADJUSTMENT)) propInventory.stock(propSkuDO.getId(), true);
    PropReviewProjection propReviewProjectionLocked = propAccess.review(type, auditId, true);
    propAccess.version(propReviewProjectionLocked.getVersion(), versionReasonDTO.expectedVersion());
    if (!CODE_PENDING.equals(propReviewProjectionLocked.getAuditStatus()))
      throw conflict(PropReasonCode.PROP_ALREADY_REVIEWED.name());
    CancelReview cancelReview =
        new com.mujian.backend.prop.dto.PropWriteDTO.CancelReview(
            auditId,
            "cancelled",
            actor,
            now(clock),
            text(versionReasonDTO.reason(), 255),
            propReviewProjectionLocked.getVersion() + 1,
            now(clock));
    switch (type) {
      case SKU_AUDIT -> propSkuAuditMapper.updateSkuAuditCancellation(cancelReview);
      case SN_ADJUSTMENT ->
          propSnAdjustmentAuditMapper.updateSnAdjustmentCancellation(cancelReview);
      case STOCK_ADJUSTMENT ->
          propStockAdjustmentAuditMapper.updateStockAdjustmentCancellation(cancelReview);
      default -> throw ApiFailure.invalid();
    }
    propCasework.cancel(type, propReviewProjectionLocked, versionReasonDTO.reason());
    CommandResultVO commandResultVOApplied =
        propRequests.result(
            "cancel_application",
            "applied",
            propSkuDO.getId(),
            type,
            auditId,
            propReviewProjectionLocked.getVersion() + 1,
            "cancelled",
            now(clock));
    propRequests.record(
        actor,
        key,
        hash,
        "cancel_application",
        commandResultVOApplied,
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.StatusVersion(
            CODE_PENDING, propReviewProjectionLocked.getVersion()),
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.StatusVersion(
            "cancelled", propReviewProjectionLocked.getVersion() + 1));
    return commandResultVOApplied;
  }
}
