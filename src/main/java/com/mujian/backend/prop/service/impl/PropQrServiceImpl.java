package com.mujian.backend.prop.service.impl;

import com.mujian.backend.common.enums.FulfillmentRole;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.PropReviewSources.*;
import static com.mujian.backend.infrastructure.persistence.MappingInputs.first;

import com.mujian.backend.common.enums.PropAssetType;
import com.mujian.backend.common.enums.PropQrTargetType;
import com.mujian.backend.common.enums.PropReasonCode;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.domain.PropQrExceptionRecordDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSnInstanceDO;
import com.mujian.backend.prop.dto.LabelConfirmDTO;
import com.mujian.backend.prop.dto.QrExceptionCreateDTO;
import com.mujian.backend.prop.dto.QrIssueDTO;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.ImageContent;
import com.mujian.backend.prop.mapper.PropQrCodeMapper;
import com.mujian.backend.prop.mapper.PropQrExceptionRecordMapper;
import com.mujian.backend.prop.mapper.PropSnInstanceMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.projection.PropFactoryProjection;
import com.mujian.backend.prop.service.PropAudit;
import com.mujian.backend.prop.service.PropCasework;
import com.mujian.backend.prop.service.PropInventory;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.service.PropQrLookup;
import com.mujian.backend.prop.service.PropQrLookup.Chain;
import com.mujian.backend.prop.service.PropQrService;
import com.mujian.backend.prop.service.PropRequests;
import com.mujian.backend.prop.utils.PropJson;
import com.mujian.backend.prop.vo.CommandResultVO;
import com.mujian.backend.prop.vo.QrVO;
import java.time.Clock;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropQrServiceImpl implements PropQrService {
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final PropQrCodeMapper propQrCodeMapper;
  private final PropQrExceptionRecordMapper propQrExceptionRecordMapper;
  private final PropAccess propAccess;
  private final PropInventory propInventory;
  private final PropRequests propRequests;
  private final PropMaterialService propMaterialService;
  private final PrivateMaterialStore privateMaterialStore;
  private final PropCasework propCasework;
  private final PropAudit propAudit;
  private final PropJson propJson;
  private final Clock clock;
  private final PropQrLookup propQrLookup;
  private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropQrServiceImpl(
      PropSnInstanceMapper propSnInstanceMapper,
      PropQrCodeMapper propQrCodeMapper,
      PropQrExceptionRecordMapper propQrExceptionRecordMapper,
      PropAccess propAccess,
      PropInventory propInventory,
      PropRequests propRequests,
      PropMaterialService propMaterialService,
      PropCasework propCasework,
      PropAudit propAudit,
      PropJson propJson,
      Clock clock,
      org.springframework.transaction.PlatformTransactionManager platformTransactionManager,
      PropQrLookup propQrLookup,
      PrivateMaterialStore privateMaterialStore) {
    this.propQrLookup = propQrLookup;
    transactionTemplate =
        new org.springframework.transaction.support.TransactionTemplate(platformTransactionManager);
    transactionTemplate.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.propSnInstanceMapper = propSnInstanceMapper;
    this.propQrCodeMapper = propQrCodeMapper;
    this.propQrExceptionRecordMapper = propQrExceptionRecordMapper;
    this.propAccess = propAccess;
    this.propInventory = propInventory;
    this.propRequests = propRequests;
    this.propMaterialService = propMaterialService;
    this.privateMaterialStore = privateMaterialStore;
    this.propCasework = propCasework;
    this.propAudit = propAudit;
    this.propJson = propJson;
    this.clock = clock;
  }

  /** 读取指定二维码记录，缺失时按资源不存在处理。 */
  public PropQrCodeDO row(String id) {
    PropQrCodeDO propQrCodeDO = propQrCodeMapper.selectPropQrCode(id(id), false);
    if (propQrCodeDO == null) throw missing();
    return propQrCodeDO;
  }

  /** 组装二维码资料、码图及当前可执行操作。 */
  public QrVO dto(String actor, PropQrCodeDO propQrCodeDO, boolean admin) {
    PropSnInstanceDO propSnInstanceDO =
        propQrCodeDO.getTargetType().equals(PropQrTargetType.SN.name())
            ? propInventory.sn(propQrCodeDO.getTargetId(), false)
            : null;
    PropSkuDO propSkuDO = propAccess.sku(propQrCodeDO.getSkuId());
    boolean bound = propQrCodeDO.getStatus().equals("bound"),
        ready = propQrCodeDO.getImageGenerationStatus().equals("ready"),
        business = false;
    try {
      if (!admin) propAccess.business(actor, propAccess.sku(propQrCodeDO.getSkuId()));
      business = !admin;
    } catch (com.mujian.backend.common.api.ApiFailure apiFailure) {
      if (apiFailure.status >= 500) throw apiFailure;
      business = false;
    }
    return new QrVO()
        .id(propQrCodeDO.getId())
        .qrCode(propQrCodeDO.getQrCodeId())
        .codeType(propQrCodeDO.getCodeType())
        .targetType(propQrCodeDO.getTargetType())
        .targetId(propQrCodeDO.getTargetId())
        .skuId(propQrCodeDO.getSkuId())
        .skuCode(propSkuDO.getSkuCode())
        .propName(propSkuDO.getPropName())
        .snCode(propSnInstanceDO == null ? null : propSnInstanceDO.getSnCode())
        .instanceStatus(propSnInstanceDO == null ? null : propSnInstanceDO.getInstanceStatus())
        .status(propQrCodeDO.getStatus())
        .version(propQrCodeDO.getVersion())
        .imageStatus(propQrCodeDO.getImageGenerationStatus())
        .image(
            bound && ready ? propMaterialService.sign(propQrCodeDO.getQrImageStorageKey()) : null)
        .canRetryImage(
            bound && !ready && (admin ? propAccess.has(actor, "prop-qr", "manage") : business))
        .canConfirmLabel(
            bound
                && ready
                && business
                && propSnInstanceDO != null
                && propInventory.pending(propSnInstanceDO.getId()) == null
                && propSnInstanceDO.getInstanceStatus().equals("pending_label"))
        .snVersion(propSnInstanceDO == null ? null : propSnInstanceDO.getVersion())
        .supersedesQrId(propQrCodeDO.getSupersedesQrCodeId());
  }

  /** 核对查看权限后返回二维码详情。 */
  public QrVO read(String actor, String id, boolean admin) {
    PropQrCodeDO propQrCodeDO = readableRow(actor, id, admin);

    PropSnInstanceDO propSnInstanceDO =
        propQrCodeDO.getTargetType().equals(PropQrTargetType.SN.name())
            ? propInventory.sn(propQrCodeDO.getTargetId(), false)
            : null;
    PropSkuDO propSkuDO = propAccess.sku(propQrCodeDO.getSkuId());
    boolean bound = propQrCodeDO.getStatus().equals("bound"),
        ready = propQrCodeDO.getImageGenerationStatus().equals("ready"),
        business = false;
    try {
      if (!admin) propAccess.business(actor, propAccess.sku(propQrCodeDO.getSkuId()));
      business = !admin;
    } catch (com.mujian.backend.common.api.ApiFailure apiFailure) {
      if (apiFailure.status >= 500) throw apiFailure;
      business = false;
    }
    return new QrVO()
        .id(propQrCodeDO.getId())
        .qrCode(propQrCodeDO.getQrCodeId())
        .codeType(propQrCodeDO.getCodeType())
        .targetType(propQrCodeDO.getTargetType())
        .targetId(propQrCodeDO.getTargetId())
        .skuId(propQrCodeDO.getSkuId())
        .skuCode(propSkuDO.getSkuCode())
        .propName(propSkuDO.getPropName())
        .snCode(propSnInstanceDO == null ? null : propSnInstanceDO.getSnCode())
        .instanceStatus(propSnInstanceDO == null ? null : propSnInstanceDO.getInstanceStatus())
        .status(propQrCodeDO.getStatus())
        .version(propQrCodeDO.getVersion())
        .imageStatus(propQrCodeDO.getImageGenerationStatus())
        .image(
            bound && ready ? propMaterialService.sign(propQrCodeDO.getQrImageStorageKey()) : null)
        .canRetryImage(
            bound && !ready && (admin ? propAccess.has(actor, "prop-qr", "manage") : business))
        .canConfirmLabel(
            bound
                && ready
                && business
                && propSnInstanceDO != null
                && propInventory.pending(propSnInstanceDO.getId()) == null
                && propSnInstanceDO.getInstanceStatus().equals("pending_label"))
        .snVersion(propSnInstanceDO == null ? null : propSnInstanceDO.getVersion())
        .supersedesQrId(propQrCodeDO.getSupersedesQrCodeId());
  }

  /** 只从已授权且仍可使用的标签记录定位私有 COS 对象。 */
  @Override
  public ImageContent readImage(String actor, String id, boolean admin) {
    PropQrCodeDO qr = readableRow(actor, id, admin);
    if (!"bound".equals(qr.getStatus()) || !"ready".equals(qr.getImageGenerationStatus())
        || qr.getQrImageStorageKey() == null) throw missing();
    return privateMaterialStore.readImage(qr.getQrImageStorageKey());
  }

  /** 二维码资料和原图共享同一读取授权。 */
  private PropQrCodeDO readableRow(String actor, String id, boolean admin) {
    PropQrCodeDO qr = row(id);
    if (admin) propAccess.admin(actor, "prop-qr", "read");
    else propAccess.own(actor, qr.getSkuId());
    return qr;
  }

  /** 根据扫码内容校验绑定状态并返回可访问的业务二维码。 */
  public QrVO resolve(String actor, String code) {

    id(code);
    PropQrCodeDO propQrCodeDO = first(propQrCodeMapper.selectPropQrCodeListByCode(code));
    if (propQrCodeDO == null) throw missing();
    propAccess.own(actor, propQrCodeDO.getSkuId());
    if (!propQrCodeDO.getStatus().equals("bound")) throw conflict(PropReasonCode.QR_REVOKED.name());

    PropSnInstanceDO propSnInstanceDO =
        propQrCodeDO.getTargetType().equals(PropQrTargetType.SN.name())
            ? propInventory.sn(propQrCodeDO.getTargetId(), false)
            : null;
    PropSkuDO propSkuDO = propAccess.sku(propQrCodeDO.getSkuId());
    boolean bound = propQrCodeDO.getStatus().equals("bound"),
        ready = propQrCodeDO.getImageGenerationStatus().equals("ready"),
        business = false;
    try {
      if (!false) propAccess.business(actor, propAccess.sku(propQrCodeDO.getSkuId()));
      business = !false;
    } catch (com.mujian.backend.common.api.ApiFailure apiFailure) {
      if (apiFailure.status >= 500) throw apiFailure;
      business = false;
    }
    return new QrVO()
        .id(propQrCodeDO.getId())
        .qrCode(propQrCodeDO.getQrCodeId())
        .codeType(propQrCodeDO.getCodeType())
        .targetType(propQrCodeDO.getTargetType())
        .targetId(propQrCodeDO.getTargetId())
        .skuId(propQrCodeDO.getSkuId())
        .skuCode(propSkuDO.getSkuCode())
        .propName(propSkuDO.getPropName())
        .snCode(propSnInstanceDO == null ? null : propSnInstanceDO.getSnCode())
        .instanceStatus(propSnInstanceDO == null ? null : propSnInstanceDO.getInstanceStatus())
        .status(propQrCodeDO.getStatus())
        .version(propQrCodeDO.getVersion())
        .imageStatus(propQrCodeDO.getImageGenerationStatus())
        .image(
            bound && ready ? propMaterialService.sign(propQrCodeDO.getQrImageStorageKey()) : null)
        .canRetryImage(
            bound && !ready && (false ? propAccess.has(actor, "prop-qr", "manage") : business))
        .canConfirmLabel(
            bound
                && ready
                && business
                && propSnInstanceDO != null
                && propInventory.pending(propSnInstanceDO.getId()) == null
                && propSnInstanceDO.getInstanceStatus().equals("pending_label"))
        .snVersion(propSnInstanceDO == null ? null : propSnInstanceDO.getVersion())
        .supersedesQrId(propQrCodeDO.getSupersedesQrCodeId());
  }

  /** 校验权限和码图状态后重置可重试的生成任务。 */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public QrVO retry(String actor, String id, boolean admin) {

    PropQrCodeDO propQrCodeDO = propQrCodeMapper.selectPropQrCode(id(id), false);
    if (propQrCodeDO == null) throw missing();
    if (admin) propAccess.admin(actor, "prop-qr", "manage");
    else propAccess.own(actor, propQrCodeDO.getSkuId());
    PropSkuDO propSkuDO = propAccess.lock(actor, propQrCodeDO.getSkuId(), admin);
    if (!admin) propAccess.business(actor, propSkuDO);
    PropQrCodeDO propQrCodeDOLocked = propQrCodeMapper.selectPropQrCode(id, true);
    if (!propQrCodeDOLocked.getStatus().equals("bound"))
      throw conflict(PropReasonCode.QR_REVOKED.name());
    if (propQrCodeDOLocked.getImageGenerationStatus().equals("failed")
        || propQrCodeDOLocked.getImageGenerationStatus().equals("generating")
            && propQrCodeDOLocked.getImageLeaseUntil() != null
            && !propQrCodeDOLocked.getImageLeaseUntil().isAfter(now(clock)))
      propQrCodeMapper.updatePropQrImageRetry(
          new com.mujian.backend.prop.dto.PropWriteDTO.RetryQrImage(
              id,
              CODE_PENDING,
              0,
              null,
              null,
              null,
              propQrCodeDOLocked.getVersion() + 1,
              now(clock)));
    PropQrCodeDO propQrCodeDOFresh = propQrCodeMapper.selectPropQrCode(id(id), false);
    if (propQrCodeDOFresh == null) throw missing();

    PropSnInstanceDO propSnInstanceDOView =
        propQrCodeDOFresh.getTargetType().equals(PropQrTargetType.SN.name())
            ? propInventory.sn(propQrCodeDOFresh.getTargetId(), false)
            : null;
    PropSkuDO propSkuDOView = propAccess.sku(propQrCodeDOFresh.getSkuId());
    boolean bound = propQrCodeDOFresh.getStatus().equals("bound"),
        ready = propQrCodeDOFresh.getImageGenerationStatus().equals("ready"),
        business = false;
    try {
      if (!admin) propAccess.business(actor, propAccess.sku(propQrCodeDOFresh.getSkuId()));
      business = !admin;
    } catch (com.mujian.backend.common.api.ApiFailure apiFailure) {
      if (apiFailure.status >= 500) throw apiFailure;
      business = false;
    }
    return new QrVO()
        .id(propQrCodeDOFresh.getId())
        .qrCode(propQrCodeDOFresh.getQrCodeId())
        .codeType(propQrCodeDOFresh.getCodeType())
        .targetType(propQrCodeDOFresh.getTargetType())
        .targetId(propQrCodeDOFresh.getTargetId())
        .skuId(propQrCodeDOFresh.getSkuId())
        .skuCode(propSkuDOView.getSkuCode())
        .propName(propSkuDOView.getPropName())
        .snCode(propSnInstanceDOView == null ? null : propSnInstanceDOView.getSnCode())
        .instanceStatus(
            propSnInstanceDOView == null ? null : propSnInstanceDOView.getInstanceStatus())
        .status(propQrCodeDOFresh.getStatus())
        .version(propQrCodeDOFresh.getVersion())
        .imageStatus(propQrCodeDOFresh.getImageGenerationStatus())
        .image(
            bound && ready
                ? propMaterialService.sign(propQrCodeDOFresh.getQrImageStorageKey())
                : null)
        .canRetryImage(
            bound && !ready && (admin ? propAccess.has(actor, "prop-qr", "manage") : business))
        .canConfirmLabel(
            bound
                && ready
                && business
                && propSnInstanceDOView != null
                && propInventory.pending(propSnInstanceDOView.getId()) == null
                && propSnInstanceDOView.getInstanceStatus().equals("pending_label"))
        .snVersion(propSnInstanceDOView == null ? null : propSnInstanceDOView.getVersion())
        .supersedesQrId(propQrCodeDOFresh.getSupersedesQrCodeId());
  }

  /** 校验二维码、资产和版本后确认贴标并记录状态变更。 */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public CommandResultVO label(
      String actor, String snId, String key, LabelConfirmDTO labelConfirmDTO) {
    PropSnInstanceDO propSnInstanceDO = propInventory.sn(snId, false);
    propAccess.own(actor, propSnInstanceDO.getSkuId());
    PropSkuDO propSkuDO = propAccess.lock(actor, propSnInstanceDO.getSkuId(), false);
    String hash = propJson.hash("label_confirm", snId, labelConfirmDTO);
    CommandResultVO commandResultVO = propRequests.replay(actor, key, hash);
    if (commandResultVO != null) return commandResultVO;
    propAccess.business(actor, propSkuDO);
    PropSnInstanceDO propSnInstanceDOLocked = propInventory.sn(snId, true);
    PropQrCodeDO propQrCodeDO =
        first(propQrCodeMapper.selectPropQrCodeListByCodeForUpdate(id(labelConfirmDTO.qrCode())));
    if (propQrCodeDO == null
        || !snId.equals(propQrCodeDO.getTargetId())
        || !PropQrTargetType.SN.name().equals(propQrCodeDO.getTargetType())
        || !propSnInstanceDOLocked.getQrCodeId().equals(propQrCodeDO.getQrCodeId()))
      throw conflict(PropReasonCode.QR_TARGET_MISMATCH.name());
    if (!"bound".equals(propQrCodeDO.getStatus())) throw conflict(PropReasonCode.QR_REVOKED.name());
    if (!"ready".equals(propQrCodeDO.getImageGenerationStatus()))
      throw conflict(PropReasonCode.QR_IMAGE_NOT_READY.name());
    propAccess.version(propSnInstanceDOLocked.getVersion(), labelConfirmDTO.expectedVersion());
    if (!"pending_label".equals(propSnInstanceDOLocked.getInstanceStatus()))
      throw conflict(PropReasonCode.SN_STATE_CONFLICT.name());
    if (propInventory.pending(snId) != null)
      throw conflict(PropReasonCode.SN_ADJUSTMENT_PENDING.name());
    String operationId = Ids.next();
    propInventory.transition(
        propSnInstanceDOLocked,
        "idle",
        actor,
        FulfillmentRole.factory.name(),
        "label_confirm",
        "扫码确认贴标",
        null,
        "prop-op:" + operationId);
    propQrCodeMapper.updatePropQrLabelConfirmation(
        new com.mujian.backend.prop.dto.PropWriteDTO.ConfirmQrLabel(
            propQrCodeDO.getId(), actor, now(clock), propQrCodeDO.getVersion() + 1, now(clock)));
    CommandResultVO commandResultVOApplied =
        propRequests.result(
            "label_confirm",
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
        "label_confirm",
        commandResultVOApplied,
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.SnStatus(
            "pending_label",
            propSnInstanceDOLocked.getVersion(),
            propSnInstanceDOLocked.getQrCodeId()),
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.SnStatus(
            "idle", propSnInstanceDOLocked.getVersion() + 1, propQrCodeDO.getQrCodeId()));
    return commandResultVOApplied;
  }

  /** 生成替换二维码并按原规则关联被替代记录。 */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public CommandResultVO issue(String actor, String key, QrIssueDTO qrIssueDTO, String trace) {
    propAccess.admin(actor, "prop-qr", "manage");
    String sid =
        qrIssueDTO.targetType().equals(PropQrTargetType.SN.name())
            ? propInventory.sn(qrIssueDTO.targetId(), false).getSkuId()
            : qrIssueDTO.targetId();
    PropSkuDO propSkuDO = propAccess.lock(actor, sid, true);
    String hash = propJson.hash("issue_qr", qrIssueDTO.targetId(), qrIssueDTO);
    CommandResultVO commandResultVO = propRequests.replay(actor, key, hash);
    if (commandResultVO != null) return commandResultVO;
    PropFactoryProjection propFactoryProjection =
        propAccess.factoryById(propSkuDO.getFactoryId(), false);
    propAccess.business(propFactoryProjection.getOwnerUserId(), propSkuDO);
    PropSnInstanceDO propSnInstanceDO = null;
    if (qrIssueDTO.targetType().equals(PropQrTargetType.SN.name())) {
      if (!propSkuDO.getAssetType().equals(PropAssetType.A_ASSET.name()))
        throw conflict(PropReasonCode.QR_TARGET_MISMATCH.name());
      propSnInstanceDO = propInventory.sn(qrIssueDTO.targetId(), true);
      if (Set.of("lost", "scrapped", "retired").contains(propSnInstanceDO.getInstanceStatus()))
        throw conflict(PropReasonCode.SN_STATE_CONFLICT.name());
    } else if (!propSkuDO.getAssetType().equals(PropAssetType.B_CONSUMABLE.name()))
      throw conflict(PropReasonCode.QR_TARGET_MISMATCH.name());
    if (first(
            propQrCodeMapper.selectActivePropQrCodeByTargetForUpdate(
                qrIssueDTO.targetType(), qrIssueDTO.targetId()))
        != null) throw conflict(PropReasonCode.QR_ALREADY_ACTIVE.name());
    Chain chain =
        propQrLookup
            .find(qrIssueDTO.targetType(), List.of(qrIssueDTO.targetId()))
            .get(qrIssueDTO.targetId());
    if (chain != null && !chain.latest().getId().equals(qrIssueDTO.supersedesQrId()))
      throw conflict(PropReasonCode.QR_TARGET_MISMATCH.name());
    if (qrIssueDTO.supersedesQrId() != null) {
      PropQrCodeDO propQrCodeDO =
          propQrCodeMapper.selectPropQrCode(id(qrIssueDTO.supersedesQrId()), true);
      if (propQrCodeDO == null
          || !qrIssueDTO.targetId().equals(propQrCodeDO.getTargetId())
          || !qrIssueDTO.targetType().equals(propQrCodeDO.getTargetType()))
        throw conflict(PropReasonCode.QR_TARGET_MISMATCH.name());
      if (!propQrCodeDO.getStatus().equals("revoked"))
        throw conflict(PropReasonCode.QR_ALREADY_ACTIVE.name());
    } else if (propQrCodeMapper.selectPropQrHistoryCountByTarget(
            qrIssueDTO.targetType(), qrIssueDTO.targetId())
        > 0) throw conflict(PropReasonCode.QR_TARGET_MISMATCH.name());
    PropQrCodeDO propQrCodeDOLocked =
        propInventory.createQrRecord(
            sid,
            qrIssueDTO.targetType(),
            qrIssueDTO.targetId(),
            actor,
            qrIssueDTO.supersedesQrId());
    if (propSnInstanceDO != null)
      propSnInstanceMapper.updatePropSnQrBinding(
          new com.mujian.backend.prop.dto.PropWriteDTO.BindSnQr(
              propSnInstanceDO.getId(),
              propQrCodeDOLocked.getQrCodeId(),
              propSnInstanceDO.getVersion() + 1,
              now(clock)));
    CommandResultVO commandResultVOApplied =
        propRequests.result(
            "issue_qr", "applied", sid, "qr", propQrCodeDOLocked.getId(), 0, "bound", now(clock));
    propRequests.record(
        actor,
        key,
        hash,
        "issue_qr",
        commandResultVOApplied,
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.PreviousQr(qrIssueDTO.supersedesQrId()),
        new com.mujian.backend.prop.dto.PropAuditFactsDTO.QrIdentity(
            propQrCodeDOLocked.getId(), propQrCodeDOLocked.getQrCodeId()));
    propAudit.record(
        actor,
        "prop_qr_code",
        propQrCodeDOLocked.getId(),
        "issue_qr",
        "admin:prop-qr:manage",
        trace,
        key,
        null,
        null,
        text(qrIssueDTO.reason(), 255),
        null,
        commandResultVOApplied);
    return commandResultVOApplied;
  }

  /** 校验权限与凭证后在事务中登记二维码异常申请。 */
  public CommandResultVO report(
      String actor, String key, QrExceptionCreateDTO qrExceptionCreateDTO) {
    propAccess.admin(actor, "prop-qr", "manage");
    CommandResultVO commandResultVO =
        propRequests.replay(
            actor,
            key,
            propJson.hash(
                "create_qr_exception", qrExceptionCreateDTO.qrId(), qrExceptionCreateDTO));
    if (commandResultVO != null) return commandResultVO;
    propMaterialService.preflight(actor, qrExceptionCreateDTO.evidence(), "qr_exception_evidence");
    return transactionTemplate.execute(
        transactionStatus -> {
          propAccess.admin(actor, "prop-qr", "manage");
          PropQrCodeDO propQrCodeDO =
              propQrCodeMapper.selectPropQrCode(id(qrExceptionCreateDTO.qrId()), false);
          if (propQrCodeDO == null) throw missing();
          PropSkuDO propSkuDO = propAccess.lock(actor, propQrCodeDO.getSkuId(), true);
          String hash =
              propJson.hash(
                  "create_qr_exception", qrExceptionCreateDTO.qrId(), qrExceptionCreateDTO);
          CommandResultVO commandResultVOLockedReplay = propRequests.replay(actor, key, hash);
          if (commandResultVOLockedReplay != null) return commandResultVOLockedReplay;
          PropQrExceptionRecordDO propQrExceptionRecordDO =
              first(
                  propQrExceptionRecordMapper.selectPendingQrExceptionListByQrId(
                      qrExceptionCreateDTO.qrId()));
          if (propQrExceptionRecordDO != null)
            return propRequests.submitted(
                QR_EXCEPTION, propQrExceptionRecordDO, "existing_pending");
          if (propQrCodeDO.getTargetType().equals(PropQrTargetType.SN.name()))
            propInventory.sn(propQrCodeDO.getTargetId(), true);
          PropQrCodeDO propQrCodeDOLocked =
              propQrCodeMapper.selectPropQrCode(qrExceptionCreateDTO.qrId(), true);
          if (!propQrCodeDOLocked.getStatus().equals("bound"))
            throw conflict(PropReasonCode.QR_REVOKED.name());
          String auditId = Ids.next();
          List<String> keys = new ArrayList<>();
          for (com.mujian.backend.prop.dto.ImageInputDTO imageInputDTO :
              qrExceptionCreateDTO.evidence())
            keys.add(propMaterialService.resolve(actor, imageInputDTO, "qr_exception_evidence"));
          propQrExceptionRecordMapper.insertPropQrExceptionRecord(
              new PropQrExceptionRecordDO()
                  .id(auditId)
                  .exceptionNo("QRX-" + auditId)
                  .factoryId(propSkuDO.getFactoryId())
                  .skuId(propSkuDO.getId())
                  .snId(
                      propQrCodeDOLocked.getTargetType().equals(PropQrTargetType.SN.name())
                          ? propQrCodeDOLocked.getTargetId()
                          : null)
                  .originalQrCodeId(propQrCodeDOLocked.getId())
                  .reportedByUserId(actor)
                  .exceptionType(qrExceptionCreateDTO.exceptionType())
                  .description(text(qrExceptionCreateDTO.description(), 500))
                  .evidenceUrlsJson(propJson.write(keys))
                  .exceptionStatus("reported")
                  .version(0)
                  .requestId(id(key))
                  .requestHash(hash)
                  .createdAt(now(clock))
                  .updatedAt(now(clock)));
          PropQrExceptionRecordDO propQrExceptionRecordDOSubmitted =
              propQrExceptionRecordMapper.selectPropQrExceptionRecord(auditId, false);
          propCasework.defer(QR_EXCEPTION, propQrExceptionRecordDOSubmitted.getId());
          return propRequests.submitted(
              QR_EXCEPTION, propQrExceptionRecordDOSubmitted, "submitted");
        });
  }
}
