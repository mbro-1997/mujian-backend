package com.mujian.backend.prop.policy;

import com.mujian.backend.common.enums.TradingReason;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.PropReviewSources.*;
import static com.mujian.backend.infrastructure.persistence.MappingInputs.first;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.FactoryReasonCode;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.common.enums.PropReasonCode;
import com.mujian.backend.factory.mapper.FactoryProfileMapper;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.prop.converter.PropReviewConverter;
import com.mujian.backend.prop.domain.PropSkuAuditDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.mapper.PropQrExceptionRecordMapper;
import com.mujian.backend.prop.mapper.PropSkuAuditMapper;
import com.mujian.backend.prop.mapper.PropSkuMapper;
import com.mujian.backend.prop.mapper.PropSnAdjustmentAuditMapper;
import com.mujian.backend.prop.mapper.PropStockAdjustmentAuditMapper;
import com.mujian.backend.prop.projection.PropFactoryProjection;
import com.mujian.backend.prop.projection.PropReviewProjection;
import com.mujian.backend.prop.service.PropFactoryGateway;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PropAccess {
  public final FactoryAccess factoryAccess;
  private final FactoryReviewMapper factoryReviewMapper;
  private final FactoryProfileMapper factoryProfileMapper;
  private final PropSkuMapper propSkuMapper;
  private final PropSkuAuditMapper propSkuAuditMapper;
  private final PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper;
  private final PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper;
  private final PropQrExceptionRecordMapper propQrExceptionRecordMapper;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropAccess(
      FactoryAccess factoryAccess,
      FactoryReviewMapper factoryReviewMapper,
      FactoryProfileMapper factoryProfileMapper,
      PropSkuMapper propSkuMapper,
      PropSkuAuditMapper propSkuAuditMapper,
      PropSnAdjustmentAuditMapper propSnAdjustmentAuditMapper,
      PropStockAdjustmentAuditMapper propStockAdjustmentAuditMapper,
      PropQrExceptionRecordMapper propQrExceptionRecordMapper) {
    this.factoryAccess = factoryAccess;
    this.factoryReviewMapper = factoryReviewMapper;
    this.factoryProfileMapper = factoryProfileMapper;
    this.propSkuMapper = propSkuMapper;
    this.propSkuAuditMapper = propSkuAuditMapper;
    this.propSnAdjustmentAuditMapper = propSnAdjustmentAuditMapper;
    this.propStockAdjustmentAuditMapper = propStockAdjustmentAuditMapper;
    this.propQrExceptionRecordMapper = propQrExceptionRecordMapper;
  }

  /** 将道具数据关系异常转换为统一错误响应。 */
  public static ApiFailure inconsistent() {
    return new ApiFailure(
        500, CODE_DATA_INTEGRITY_ERROR, PropReasonCode.PROP_DATA_INCONSISTENT.name());
  }

  /** 将受控业务来源映射到既有关系标识。 */
  public static String table(String source) {
    return switch (source) {
      case SKU_AUDIT -> "prop_sku_audit";
      case SN_ADJUSTMENT -> "prop_sn_adjustment_audit";
      case STOCK_ADJUSTMENT -> "prop_stock_adjustment_audit";
      case QR_EXCEPTION -> "prop_qr_exception_record";
      default -> throw ApiFailure.invalid();
    };
  }

  /** 校验当前账号的工厂身份并按需读取锁定资料。 */
  public PropFactoryProjection factory(String actor, boolean write, boolean lock) {
    try {
      PropFactoryProjection propFactoryProjection =
          PropFactoryGateway.project(factoryAccess.profile(actor, write, lock));
      if (write) operating(propFactoryProjection);
      return propFactoryProjection;
    } catch (ApiFailure apiFailure) {
      if (FactoryReasonCode.FACTORY_DATA_INCONSISTENT.name().equals(apiFailure.reason))
        throw inconsistent();
      throw apiFailure;
    }
  }

  /** 按工厂标识读取资料，缺失时拒绝继续处理。 */
  public PropFactoryProjection factoryById(String id, boolean lock) {
    PropFactoryProjection propFactoryProjection =
        PropFactoryGateway.project(
            factoryReviewMapper.selectFactorySourceById(FactorySource.PROFILE, id, lock));
    if (propFactoryProjection == null) throw missing();
    return propFactoryProjection;
  }

  /** 拒绝未处于正常经营状态的工厂执行业务写入。 */
  public void operating(PropFactoryProjection propFactoryProjection) {
    if (!CODE_ACTIVE.equals(propFactoryProjection.getFactoryStatus()))
      throw ApiFailure.denied(PropReasonCode.FACTORY_NOT_OPERATING.name());
  }

  /** 校验当前账号具备租赁方身份。 */
  public void demander(String actor) {
    if (!CODE_DEMANDER.equals(factoryAccess.context(actor, false).roleCode()))
      throw ApiFailure.denied(TradingReason.DEMANDER_ROLE_REQUIRED.name());
  }

  /** 读取指定道具，缺失时按资源不存在处理。 */
  public PropSkuDO sku(String id) {
    PropSkuDO propSkuDO = propSkuMapper.selectPropSku(id(id), false);
    if (propSkuDO == null) throw missing();
    return propSkuDO;
  }

  /** 核对道具属于当前工厂，防止跨工厂访问。 */
  public PropSkuDO own(String actor, String sku) {
    PropFactoryProjection propFactoryProjection = factory(actor, false, false);
    PropSkuDO propSkuDO = sku(sku);
    if (!propFactoryProjection.getId().equals(propSkuDO.getFactoryId())) throw missing();
    return propSkuDO;
  }

  /** 写操作依次锁定用户、工厂、SKU、SN 或库存、来源、二维码及管理事项。 */
  public PropSkuDO lock(String actor, String sku, boolean admin) {
    PropSkuDO propSkuDO = sku(sku);
    PropFactoryProjection propFactoryProjection = factoryById(propSkuDO.getFactoryId(), false);
    if (!admin && !actor.equals(propFactoryProjection.getOwnerUserId())) throw missing();
    factoryAccess.lock(actor, propFactoryProjection.getOwnerUserId());
    factoryById(propFactoryProjection.getId(), true);
    PropSkuDO propSkuDOLocked = propSkuMapper.selectPropSku(sku, true);
    if (propSkuDOLocked == null) throw missing();
    return propSkuDOLocked;
  }

  /** 核对工厂经营状态及道具是否允许操作。 */
  public void business(String actor, PropSkuDO propSkuDO) {
    business(actor, propSkuDO, PropReasonCode.SKU_AUDITING, PropReasonCode.SKU_STATE_CONFLICT);
  }

  /** 资料更新复用相同经营边界，返回B契约规定的更新原因。 */
  public void changeAllowed(String actor, PropSkuDO propSkuDO) {
    business(
        actor, propSkuDO, PropReasonCode.SKU_REVIEW_PENDING, PropReasonCode.SKU_CHANGE_NOT_ALLOWED);
  }

  /** 所有同类入口采用相同状态判断，原因码由各正式契约决定。 */
  private void business(
      String actor, PropSkuDO propSkuDO, PropReasonCode pendingReason, PropReasonCode stateReason) {
    factoryAccess.context(actor, true);
    operating(factoryById(propSkuDO.getFactoryId(), false));
    if (pending(propSkuDO.getId()) != null) throw conflict(pendingReason.name());
    if (!Set.of("online", "offline").contains(propSkuDO.getSkuStatus()))
      throw conflict(stateReason.name());
  }

  /** 读取当前业务对象尚未处理的申请，阻止冲突操作。 */
  public PropSkuAuditDO pending(String sku) {
    return first(propSkuAuditMapper.selectPendingPropSkuAuditListBySkuId(sku));
  }

  /** 校验审核来源与归属后读取对应业务记录。 */
  public PropReviewProjection source(String actor, String type, String id, boolean admin) {
    PropReviewProjection propReviewProjection = review(type, id(id), false);
    if (propReviewProjection == null) throw missing();
    if (!admin) own(actor, propReviewProjection.getSkuId());
    return propReviewProjection;
  }

  /** 校验管理员对指定道具业务的操作权限。 */
  public void admin(String actor, String domain, String action) {
    factoryAccess.admin(actor, domain, action);
  }

  /** 判断当前管理员是否具备指定业务权限。 */
  public boolean has(String actor, String domain, String action) {
    return factoryAccess.has(actor, domain, action);
  }

  /** 核对提交版本，拒绝覆盖已经变更的记录。 */
  public void version(int actual, Integer expected) {
    if (expected == null || actual != expected) throw conflict(CODE_VERSION_CONFLICT);
  }

  /** 将各类道具申请读取为统一审核投影。 */
  public PropReviewProjection review(String type, String id, boolean lock) {
    return switch (type) {
      case SKU_AUDIT -> PropReviewConverter.from(propSkuAuditMapper.selectPropSkuAudit(id, lock));
      case SN_ADJUSTMENT ->
          PropReviewConverter.from(
              propSnAdjustmentAuditMapper.selectPropSnAdjustmentAudit(id, lock));
      case STOCK_ADJUSTMENT ->
          PropReviewConverter.from(
              propStockAdjustmentAuditMapper.selectPropStockAdjustmentAudit(id, lock));
      case QR_EXCEPTION ->
          PropReviewConverter.from(
              propQrExceptionRecordMapper.selectPropQrExceptionRecord(id, lock));
      default -> throw ApiFailure.invalid();
    };
  }

  /** 本次只读请求的账号及工厂状态，不代替写事务中的锁内校验。 */
  public record FactoryReadContext(PropFactoryProjection factory, boolean accountActive) {}

  /** 一次读取工厂身份及资料，整页操作标志复用同一事务快照。 */
  public FactoryReadContext factoryReadContext(String actor) {
    com.mujian.backend.web.response.account.ContextResponse contextResponse =
        factoryAccess.context(actor, false);
    if (!CODE_SUPPLIER.equals(contextResponse.roleCode()))
      throw ApiFailure.denied(FactoryReasonCode.FACTORY_ROLE_NOT_ELIGIBLE.name());
    PropFactoryProjection propFactoryProjection =
        PropFactoryGateway.project(factoryProfileMapper.selectFactoryProfileByOwner(actor, false));
    if (propFactoryProjection == null) throw inconsistent();
    return new FactoryReadContext(
        propFactoryProjection, CODE_ACTIVE.equals(contextResponse.userStatus()));
  }

  /** 用已读取状态计算展示能力，不执行数据库查询。 */
  public boolean canOperate(
      FactoryReadContext factoryReadContext, PropSkuDO propSkuDO, boolean pending) {
    return factoryReadContext != null
        && factoryReadContext.accountActive()
        && factoryReadContext.factory().getId().equals(propSkuDO.getFactoryId())
        && CODE_ACTIVE.equals(factoryReadContext.factory().getFactoryStatus())
        && !pending
        && Set.of("online", "offline").contains(propSkuDO.getSkuStatus());
  }
}
