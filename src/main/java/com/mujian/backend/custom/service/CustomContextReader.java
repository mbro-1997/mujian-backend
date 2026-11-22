package com.mujian.backend.custom.service;

import com.mujian.backend.custom.dto.CustomForms;
import java.util.Map;

import static com.mujian.backend.common.support.D2Values.missing;

import com.mujian.backend.common.api.*;
import com.mujian.backend.custom.mapper.*;
import com.mujian.backend.custom.mapper.CustomContextMapper.*;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.service.TradingAccess;
import com.mujian.backend.payment.service.RefundReader;
import com.mujian.backend.payment.vo.RefundViews;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CustomContextReader {
  private final CustomContextMapper customContextMapper;
  private final CustomFulfillmentMapper customFulfillmentMapper;
  private final CustomPurchase customPurchase;
  private final TradingAccess tradingAccess;
  private final VerificationAccess verificationAccess;
  private final RefundReader refundReader;
  private final CustomMaterials customMaterials;

  public CustomContextReader(
      CustomMaterials customMaterials,
      CustomContextMapper customContextMapper,
      CustomFulfillmentMapper customFulfillmentMapper,
      CustomPurchase customPurchase,
      TradingAccess tradingAccess,
      VerificationAccess verificationAccess,
      RefundReader refundReader) {
    this.customMaterials = customMaterials;
    this.customContextMapper = customContextMapper;
    this.customFulfillmentMapper = customFulfillmentMapper;
    this.customPurchase = customPurchase;
    this.tradingAccess = tradingAccess;
    this.verificationAccess = verificationAccess;
    this.refundReader = refundReader;
  }

  public record CaseView(
      String caseId,
      int caseVersion,
      String state,
      List<Note> notes,
      List<StatementView> factoryStatements) {}

  public record StatementView(
      String id,
      String summary,
      List<com.mujian.backend.custom.dto.CustomForms.EvidenceRef> evidence,
      String at) {}

  public record Statements(boolean canSubmit, List<StatementView> items) {}

  /** 读取来源关联的工厂说明。 */
  private List<StatementView> statements(String sale, String order) {
    Map<String,CustomForms.EvidenceRef> files = customMaterials.index(order);
    return customContextMapper.selectFactoryStatementsByAfterSale(sale).stream()
        .map(
            s ->
                new StatementView(
                    s.id(), s.summary(), customMaterials.references(s.evidence(), files), s.at()))
        .toList();
  }

  /** 校验工厂归属后读取售后说明。 */
  public Statements factoryStatements(String actor, String id) {
    CustomFulfillmentMapper.Sale sale = customFulfillmentMapper.selectCustomAfterSaleRequestById(id);
    if (sale == null) throw missing();
    customPurchase.owned(actor, true, sale.orderId(), false);
    return new Statements(
        java.util.Set.of("submitted", "processing", "waiting_factory").contains(sale.state()),
        statements(id, sale.orderId()));
  }

  /** 按申请来源读取关联工单及说明。 */
  public CaseView caseView(String actor, String source, boolean extension) {
    verificationAccess.admin(actor, extension ? "admin:custom:extension-read" : "admin:custom:after-sale-read");
    String caseId;
    String orderId = null;
    if (extension) {
      CustomFulfillmentMapper.Extension sourceExtension = customFulfillmentMapper.selectCustomOrderExtensionRequestById(source);
      if (sourceExtension == null || sourceExtension.caseId() == null) throw missing();
      caseId = sourceExtension.caseId();
    } else {
      CustomFulfillmentMapper.Sale customFulfillmentSale = customFulfillmentMapper.selectCustomAfterSaleRequestById(source);
      if (customFulfillmentSale == null) throw missing();
      caseId = customFulfillmentSale.caseId();
      orderId = customFulfillmentSale.orderId();
    }
    CustomContextMapper.Case customContextCase = customContextMapper.caseById(caseId);
    return new CaseView(
        customContextCase.id(),
        customContextCase.version(),
        customContextCase.state(),
        customContextMapper.selectNotesByCase(customContextCase.id()),
        extension ? List.of() : statements(source, orderId));
  }

  /** 分页返回可访问的工厂。 */
  public PageResult<Factory> factories(String actor, PageQuery page) {
    tradingAccess.buyer(actor);
    return PageResult.of(customContextMapper.selectCustomContextFactories(page), customContextMapper.factoryCount(), page);
  }

  /** 核对身份后读取指定工厂。 */
  public Factory factory(String actor, String id) {
    tradingAccess.buyer(actor);
    CustomContextMapper.Factory factory = customContextMapper.selectCustomContextFactory(id);
    if (factory == null) throw missing();
    return factory;
  }

  /** 读取原订单关联的退款进度。 */
  public List<RefundViews.Refund> refunds(String actor, String id) {
    customPurchase.owned(actor, false, id, false);
    return refundReader.authorizedList(customContextMapper.selectCustomRefundsByOrderId(id));
  }
}
