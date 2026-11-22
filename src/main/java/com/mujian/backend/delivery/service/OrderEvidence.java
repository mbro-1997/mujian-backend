package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.domain.OrderEvidenceDO;
import com.mujian.backend.delivery.dto.EvidenceDtos;
import com.mujian.backend.delivery.mapper.EvidenceMapper;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.delivery.vo.EvidenceViews;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore;
import com.mujian.backend.material.service.*;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

/** 原凭证、本单权限和私有对象三方核对，外部存储访问在业务事务之外。 */
@Service
public class OrderEvidence {
  private static final String PURPOSE = "order_evidence", VERSION = "order-evidence-material-v1";
  private final FulfillmentAccess fulfillmentAccess;
  private final EvidenceRoots evidenceRoots;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final FulfillmentProtection fulfillmentProtection;
  private final EvidenceMapper evidenceMapper;
  private final VerificationMaterials verificationMaterials;
  private final VerificationAccess verificationAccess;
  private final PrivateMaterialStore privateMaterialStore;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final tools.jackson.databind.ObjectMapper objectMapper;

  /** 沿用认证材料私有存储、清理凭证和本人交易回执。 */
  public OrderEvidence(
      FulfillmentAccess fulfillmentAccess,
      EvidenceRoots evidenceRoots,
      TradingRoots tradingRoots,
      TradingOperations tradingOperations,
      FulfillmentProtection fulfillmentProtection,
      EvidenceMapper evidenceMapper,
      VerificationMaterials verificationMaterials,
      VerificationAccess verificationAccess,
      PrivateMaterialStore privateMaterialStore,
      PlatformTransactionManager manager,
      Clock clock,
      tools.jackson.databind.ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.fulfillmentAccess = fulfillmentAccess;
    this.evidenceRoots = evidenceRoots;
    this.tradingRoots = tradingRoots;
    this.tradingOperations = tradingOperations;
    this.fulfillmentProtection = fulfillmentProtection;
    this.evidenceMapper = evidenceMapper;
    this.verificationMaterials = verificationMaterials;
    this.verificationAccess = verificationAccess;
    this.privateMaterialStore = privateMaterialStore;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 授权订单的临时照片，返回24小时凭证，上传本身不推进业务状态。 */
  public EvidenceViews.Upload upload(
      String actor, String orderId, FulfillmentRole role, EvidenceNode node, byte[] bytes) {
    fulfillmentAccess.order(actor, orderId, role == FulfillmentRole.factory, null);
    evidenceRoots.authorize(orderId, node, role);
    try {
      VerificationMaterials.OrderUpload upload =
          verificationMaterials.uploadOrder(
              actor,
              new VerificationMaterials.EvidenceScope(orderId, node.name(), role.name()),
              bytes);
      return new EvidenceViews.Upload(
          upload.receipt().uploadId(),
          time(LocalDateTime.ofInstant(upload.receipt().acceptUntil(), ZONE)),
          upload.mimeType(),
          upload.sizeBytes());
    } catch (ApiFailure failure) {
      throw mapped(failure);
    }
  }

  /** 订单争议申请在事务外校验私有文件；提交中再次检查持久凭证。 */
  public List<VerificationMaterials.VerifiedObject> verifyUploads(String actor,String orderId,EvidenceNode node,
      FulfillmentRole role,List<String> uploadIds) {
    List<VerificationMaterials.VerifiedObject> verifiedObjects=new ArrayList<>();
    try {
      for (String uploadId: uploadIds) {
        VerificationMaterials.VerifiedObject verifiedObject=verificationMaterials.verified(actor,PURPOSE,VERSION,uploadId);
        if (!new VerificationMaterials.EvidenceScope(orderId,node.name(),role.name()).equals(verifiedObject.receipt().evidenceScope()))
          throw invalidMaterial();
        verifiedObjects.add(verifiedObject);
      }
    } catch (ApiFailure failure) { throw mapped(failure); }
    return verifiedObjects;
  }

  /** 在来源写入事务内消费原凭证，一张照片只存一个私有对象和证据行。 */
  @Transactional(propagation=Propagation.MANDATORY)
  public List<String> bindVerifiedUploads(String actor,String orderId,EvidenceNode node,FulfillmentRole role,
      List<VerificationMaterials.VerifiedObject> objects) {
    List<String> evidenceIds=new ArrayList<>();
    try {
      for (VerificationMaterials.VerifiedObject object: objects) {
        VerificationMaterials.Receipt local=verificationMaterials.localReceipt(actor,PURPOSE,VERSION,object.receipt().uploadId());
        if (!local.equals(object.receipt()) || !new VerificationMaterials.EvidenceScope(orderId,node.name(),role.name()).equals(local.evidenceScope())
            || evidenceMapper.receiptUsed(local.uploadId())) throw invalidMaterial();
        verificationMaterials.validateNewReference(local);
        String evidenceId=Ids.next();
        evidenceMapper.insertEvidence(new OrderEvidenceDO(evidenceId,orderId,null,null,null,null,node.name(),null,
            local.objectKey(),local.uploadId(),object.versionId(),null,actor,role.name(),null,
            LocalDateTime.ofInstant(local.acceptedAt(),ZONE),null,null,null,null));
        evidenceIds.add(evidenceId);
      }
    } catch (ApiFailure failure) { throw mapped(failure); }
    return evidenceIds;
  }

  /** 先读已获权回执再预检COS，并在订单锁内重新检查版本与原凭证。 */
  public TradingReceipt attach(
      TradingCommand tradingCommand, String orderId, FulfillmentRole role, EvidenceDtos.Attach attach) {
    fulfillmentAccess.order(tradingCommand.actor(), orderId, role == FulfillmentRole.factory, null);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    if (attach.materials() == null || attach.materials().isEmpty() || attach.materials().size() > 20)
      throw ApiFailure.invalid();
    EvidenceDtos.Input first = attach.materials().get(0);
    evidenceRoots.authorize(orderId, first.node(), role);
    String digest =
        new com.mujian.backend.order.utils.TradingJson(objectMapper)
            .hash(
                tradingCommand.operation().name(),
                "POST",
                orderId,
                objectMapper.valueToTree(
                    attach.materials().stream()
                        .sorted(Comparator.comparing(EvidenceDtos.Input::receiptId))
                        .toList()));
    TradingReceipt previous = tradingOperations.priorBinding(tradingCommand, digest);
    if (previous != null) return previous;
    boolean expense = first.node() == EvidenceNode.transport_expense;
    Map<String, VerificationMaterials.VerifiedObject> objects = new LinkedHashMap<>();
    try {
      for (EvidenceDtos.Input part : attach.materials()) {
        if (part.node() != first.node()
            || !Objects.equals(first.statementId(), part.statementId())
            || objects.containsKey(part.receiptId())
            || !expense && (part.statementId() != null || part.transportLegId() != null))
          throw ApiFailure.invalid();
        VerificationMaterials.VerifiedObject object =
            verificationMaterials.verified(tradingCommand.actor(), PURPOSE, VERSION, part.receiptId());
        if (!new VerificationMaterials.EvidenceScope(orderId, part.node().name(), role.name())
            .equals(object.receipt().evidenceScope())) throw invalidMaterial();
        objects.put(part.receiptId(), object);
      }
      return tx.execute(status -> bind(tradingCommand, orderId, role, attach, objects, digest));
    } catch (ApiFailure failure) {
      TradingReceipt applied = tradingOperations.replay(tradingCommand);
      if (applied != null) return applied;
      throw mapped(failure);
    }
  }

  /** 所有材料同事务插入，局部失败撤销草稿、引用及版本。 */
  private TradingReceipt bind(
      TradingCommand tradingCommand,
      String orderId,
      FulfillmentRole role,
      EvidenceDtos.Attach attach,
      Map<String, VerificationMaterials.VerifiedObject> objects,
      String digest) {
    RentalOrderDO order =
        fulfillmentAccess.order(tradingCommand.actor(), orderId, role == FulfillmentRole.factory, null);
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    TradingReceipt previous = tradingOperations.priorBinding(tradingCommand, digest);
    if (previous != null) return previous;
    EvidenceDtos.Input first = attach.materials().get(0);
    if (first.node()==EvidenceNode.return_sent || first.node()==EvidenceNode.return_check) fulfillmentProtection.requireReturn(orderId);
    else fulfillmentProtection.requireNormal(orderId);
    EvidenceRoots.Root root =
        evidenceRoots.resolve(
            order,
            tradingCommand.actor(),
            role,
            first.node(),
            first.statementId(),
            first.transportLegId(),
            attach.expectedVersion());
    for (EvidenceDtos.Input part : attach.materials()) {
      VerificationMaterials.Receipt local =
          verificationMaterials.localReceipt(tradingCommand.actor(), PURPOSE, VERSION, part.receiptId());
      VerificationMaterials.VerifiedObject object = objects.get(part.receiptId());
      if (!local.equals(object.receipt()) || evidenceMapper.receiptUsed(part.receiptId()))
        throw invalidMaterial();
      verificationMaterials.validateNewReference(local);
      LocalDateTime captured = ReturnShipments.parseTime(part.capturedAt());
      if (captured != null && captured.isAfter(now(clock))) throw ApiFailure.invalid();
      if (part.itemId() != null && !evidenceMapper.ownsItem(orderId, part.itemId()))
        throw missing();
      String leg = root.legId();
      if (part.node() == EvidenceNode.transport_expense)
        leg =
            evidenceRoots
                .resolve(
                    order,
                    tradingCommand.actor(),
                    role,
                    part.node(),
                    part.statementId(),
                    part.transportLegId(),
                    attach.expectedVersion())
                .legId();
      evidenceMapper.insertEvidence(
          new OrderEvidenceDO(
              Ids.next(),
              orderId,
              part.itemId(),
              leg,
              part.statementId(),
              part.node() == EvidenceNode.return_check ? root.id() : null,
              part.node().name(),
              part.scene(),
              local.objectKey(),
              part.receiptId(),
              object.versionId(),
              part.text(),
              tradingCommand.actor(),
              role.name(),
              captured,
              LocalDateTime.ofInstant(local.acceptedAt(), ZONE),
              null,null,null,null));
    }
    if (evidenceMapper.advanceRoot(
            first.node().name(), root.id(), orderId, root.version(), now(clock))
        != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.recordBinding(
        tradingCommand, root.type().name(), root.id(), root.version() + 1, digest);
  }

  /** 只允许原节点角色移除仍在草稿内的本单照片。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt remove(
      TradingCommand tradingCommand, String orderId, String evidenceId, FulfillmentRole role, int version) {
    RentalOrderDO order =
        fulfillmentAccess.order(tradingCommand.actor(), orderId, role == FulfillmentRole.factory, null);
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    OrderEvidenceDO orderEvidenceDO =
        evidenceMapper.selectById(
            query(tradingCommand.actor(), orderId, role, null, null, PageQuery.of(null, null)),
            id(evidenceId));
    if (orderEvidenceDO == null || !orderEvidenceDO.owner().equals(tradingCommand.actor())) throw missing();
    EvidenceNode node = EvidenceNode.valueOf(orderEvidenceDO.node());
    if (node==EvidenceNode.return_sent || node==EvidenceNode.return_check) fulfillmentProtection.requireReturn(orderId);
    else fulfillmentProtection.requireNormal(orderId);
    EvidenceRoots.Root root =
        evidenceRoots.resolve(
            order,
            tradingCommand.actor(),
            role,
            node,
            orderEvidenceDO.statementId(),
            node == EvidenceNode.transport_expense ? orderEvidenceDO.legId() : null,
            version);
    if (!root.removable() || evidenceMapper.markOrderEvidenceRemoved(orderId, orderEvidenceDO.id(), now(clock)) != 1)
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    return finish(tradingCommand, orderId, node, root);
  }

  /** 证据根版本及交易回执与引用同事务落地。 */
  private TradingReceipt finish(
      TradingCommand tradingCommand, String orderId, EvidenceNode node, EvidenceRoots.Root root) {
    if (evidenceMapper.advanceRoot(node.name(), root.id(), orderId, root.version(), now(clock))
        != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.record(tradingCommand, root.type().name(), root.id(), root.version() + 1);
  }

  /** 历史费用复用集合按同单、同原证据查询；买家不可通过过滤器访问费用证据。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public EvidenceViews.Page page(
      String actor,
      String orderId,
      FulfillmentRole role,
      EvidenceNode node,
      String statementId,
      PageQuery page) {
    EvidenceMapper.Query evidenceQuery = query(actor, orderId, role, node, statementId, page);
    return new EvidenceViews.Page(
        evidenceMapper.selectPage(evidenceQuery).stream().map(OrderEvidenceDO::visible).toList(),
        page.page(),
        page.pageSize(),
        Long.toString(evidenceMapper.countPage(evidenceQuery)));
  }

  /** 获权后每次重新签发120秒私有查看地址。 */
  public EvidenceViews.View view(
      String actor, String orderId, FulfillmentRole role, String evidenceId) {
    OrderEvidenceDO orderEvidenceDO =
        evidenceMapper.selectById(
            query(actor, orderId, role, null, null, PageQuery.of(null, null)), id(evidenceId));
    if (orderEvidenceDO == null) throw missing();
    Instant expires = clock.instant().plusSeconds(120);
    try {
      return new EvidenceViews.View(
          privateMaterialStore.sign(orderEvidenceDO.storageKey(), expires),
          time(LocalDateTime.ofInstant(expires, ZONE)));
    } catch (ApiFailure failure) {
      throw mapped(failure);
    }
  }

  /** 先查订单权限，再收窄公开节点。 */
  private EvidenceMapper.Query query(
      String actor,
      String orderId,
      FulfillmentRole role,
      EvidenceNode node,
      String statementId,
      PageQuery page) {
    fulfillmentAccess.order(
        actor,
        orderId,
        role == FulfillmentRole.factory,
        role == FulfillmentRole.admin ? "admin:trade:order:read" : null);
    boolean expenses =
        role == FulfillmentRole.factory
            || role == FulfillmentRole.admin
                && verificationAccess.has(actor, "admin:fulfillment:expense:read");
    if ((node == EvidenceNode.transport_expense || statementId != null) && !expenses)
      throw missing();
    return new EvidenceMapper.Query(
        orderId, node == null ? null : node.name(), statementId, expenses, actor,role.name(),
        role==FulfillmentRole.admin && verificationAccess.has(actor,"admin:claim:read"),
        role==FulfillmentRole.admin && verificationAccess.has(actor,"admin:arbitration:read"),
        role==FulfillmentRole.admin && verificationAccess.has(actor,"admin:receipt-issue:read"),page);
  }

  /** 原材料错误映射为已确认D4契约。 */
  private ApiFailure mapped(ApiFailure failure) {
    return switch (failure.reason) {
      case D2Codes.CODE_MATERIAL_REFERENCE_EXPIRED -> conflict(FulfillmentReason.MATERIAL_EXPIRED.name());
      case D2Codes.CODE_MATERIAL_REFERENCE_INVALID -> invalidMaterial();
      case D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE ->
          unavailable(FulfillmentReason.PRIVATE_STORAGE_UNAVAILABLE.name(), failure);
      default -> failure;
    };
  }

  /** 已消费、跨人、跨单或跨节点凭证统一拒绝。 */
  private ApiFailure invalidMaterial() {
    return conflict(FulfillmentReason.MATERIAL_REFERENCE_INVALID.name());
  }
}
