package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.domain.ExpenseStatementDO;
import com.mujian.backend.delivery.dto.ExpenseDtos;
import com.mujian.backend.delivery.mapper.ExpenseMapper;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.delivery.vo.ExpenseViews;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.payment.service.NormalOrderFunds;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 当前版本及证据、审核、运费池、原路退款义务以订单根为并发边界。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class TransportExpenses {
  private final ExpenseMapper expenseMapper;
  private final ExpenseRules expenseRules;
  private final FulfillmentAccess fulfillmentAccess;
  private final VerificationAccess verificationAccess;
  private final TradingRoots tradingRoots;
  private final RentalOrderMapper rentalOrderMapper;
  private final TradingOperations tradingOperations;
  private final FulfillmentProtection fulfillmentProtection;
  private final NormalOrderFunds normalOrderFunds;
  private final FulfillmentEvents fulfillmentEvents;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private static final String READ = "admin:fulfillment:expense:read",
      REVIEW = "admin:fulfillment:expense:review";
  private static final String RESOURCE = TradingResource.order_transport_expense_statement.name();

  /** 装配往返运费核销所需依赖。 */
  public TransportExpenses(
      ExpenseMapper expenseMapper,
      ExpenseRules expenseRules,
      FulfillmentAccess fulfillmentAccess,
      VerificationAccess verificationAccess,
      TradingRoots tradingRoots,
      RentalOrderMapper rentalOrderMapper,
      TradingOperations tradingOperations,
      FulfillmentProtection fulfillmentProtection,
      NormalOrderFunds normalOrderFunds,
      FulfillmentEvents fulfillmentEvents,
      ObjectMapper objectMapper,
      Clock clock) {
    this.expenseMapper = expenseMapper;
    this.expenseRules = expenseRules;
    this.fulfillmentAccess = fulfillmentAccess;
    this.verificationAccess = verificationAccess;
    this.tradingRoots = tradingRoots;
    this.rentalOrderMapper = rentalOrderMapper;
    this.tradingOperations = tradingOperations;
    this.fulfillmentProtection = fulfillmentProtection;
    this.normalOrderFunds = normalOrderFunds;
    this.fulfillmentEvents = fulfillmentEvents;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 按原方向取得往返两段的冻结计费依据。 */
  private List<ExpenseMapper.Basis> legs(String order) {
    List<ExpenseMapper.Basis> values = expenseMapper.selectTransportLegsByOrder(order);
    if (values.size() != 2
        || !values.get(0).type().equals("outbound")
        || !values.get(1).type().equals("return"))
      throw new ApiFailure(
          500, D2Codes.CODE_DATA_INTEGRITY_ERROR, FulfillmentReason.FULFILLMENT_DATA_INCONSISTENT.name());
    return values;
  }

  /** 从原冻结运输段生成尚未填报的表单。 */
  private ExpenseViews.Leg blank(ExpenseMapper.Basis leg) {
    return new ExpenseViews.Leg(
        leg.id(),
        leg.mode(),
        null,
        null,
        null,
        expenseRules.ownPrice(leg.mode(), leg.snapshot()),
        0,
        List.of(),
        0,
        0);
  }

  /** 按工厂归属读取两段冻结费用与最新申报。 */
  public ExpenseViews.Context context(String actor, String order) {
    fulfillmentAccess.order(actor, order, true, null);
    List<ExpenseMapper.Basis> legs = legs(order);
    ExpenseStatementDO latest = expenseMapper.selectLatestExpenseStatement(order);
    return new ExpenseViews.Context(
        order,
        legs.stream().anyMatch(l -> "freight_pool".equals(l.mode())),
        latest == null ? null : view(latest, false, false),
        blank(legs.get(0)),
        blank(legs.get(1)));
  }

  /** 校验查看范围并取得原记录的金额和处理事实。 */
  public ExpenseViews.Expense detail(String actor, String order, String statement, boolean admin) {
    fulfillmentAccess.order(actor, order, !admin, admin ? READ : null);
    ExpenseStatementDO expenseStatementDO = expenseMapper.selectExpenseDetail(order, statement);
    if (expenseStatementDO == null) throw missing();
    return view(expenseStatementDO, admin, admin && verificationAccess.has(actor, REVIEW));
  }

  /** 在授权数据范围内分页并返回总数。 */
  public PageResult<ExpenseViews.Expense> page(
      String actor, ExpenseDtos.Query expenseQuery, boolean admin) {
    if (expenseQuery.orderId() != null)
      fulfillmentAccess.order(actor, expenseQuery.orderId(), !admin, admin ? READ : null);
    else verificationAccess.admin(actor, READ);
    boolean mayReview = admin && verificationAccess.has(actor, REVIEW);
    List<ExpenseViews.Expense> items =
        expenseMapper.selectExpenseStatementPageByQuery(expenseQuery).stream().map(row -> view(row, admin, mayReview)).toList();
    return PageResult.of(items, expenseMapper.selectOrderTransportExpenseStatementCountByQuery(expenseQuery), expenseQuery.page());
  }

  /** 组合往返计价、证据、资金责任和当前合法动作。 */
  private ExpenseViews.Expense view(ExpenseStatementDO expenseStatementDO, boolean admin, boolean mayReview) {
    ExpenseStatementDO.Details details =
        objectMapper.readValue(expenseStatementDO.detailJson(), ExpenseStatementDO.Details.class);
    ExpenseViews.Leg outbound =
        new ExpenseViews.Leg(
            expenseStatementDO.outboundId(),
            expenseStatementDO.outboundMode(),
            expenseStatementDO.outboundSource(),
            expenseStatementDO.outboundCarrier(),
            expenseStatementDO.outboundOrderNo(),
            expenseRules.ownPrice(expenseStatementDO.outboundMode(), expenseStatementDO.outboundSnapshot()),
            expenseStatementDO.outboundBase(),
            details.outbound().extraFees(),
            expenseStatementDO.outboundTotal(),
            expenseStatementDO.approvedOutbound());
    ExpenseViews.Leg returning =
        new ExpenseViews.Leg(
            expenseStatementDO.returnId(),
            expenseStatementDO.returnMode(),
            expenseStatementDO.returnSource(),
            expenseStatementDO.returnCarrier(),
            expenseStatementDO.returnOrderNo(),
            expenseRules.ownPrice(expenseStatementDO.returnMode(), expenseStatementDO.returnSnapshot()),
            expenseStatementDO.returnBase(),
            details.returnLeg().extraFees(),
            expenseStatementDO.returnTotal(),
            expenseStatementDO.approvedReturn());
    List<String> actions = new ArrayList<>(List.of("view_evidence"));
    if (fulfillmentProtection.writable(fulfillmentProtection.facts(expenseStatementDO.orderId()))) {
      if (admin && mayReview && List.of("submitted", "reviewing").contains(expenseStatementDO.status()))
        actions.addAll(List.of("approve", "return"));
      if (!admin && "draft".equals(expenseStatementDO.status())) {
        actions.add("save");
        if ("completed".equals(expenseStatementDO.outboundStatus()) && "completed".equals(expenseStatementDO.returnStatus()))
          actions.add("submit");
      }
      if (!admin && "returned".equals(expenseStatementDO.status()) && expenseStatementDO.latestVersion()) actions.add("revise");
    }
    List<String> refunds =
        expenseStatementDO.refundJson() == null
            ? List.of()
            : Arrays.stream(objectMapper.readValue(expenseStatementDO.refundJson(), String[].class))
                .sorted()
                .toList();
    return new ExpenseViews.Expense(
        expenseStatementDO.id(),
        expenseStatementDO.orderId(),
        expenseStatementDO.orderNo(),
        expenseStatementDO.factoryId(),
        expenseStatementDO.factoryName(),
        expenseStatementDO.statementVersion(),
        expenseStatementDO.version(),
        expenseStatementDO.status(),
        outbound,
        returning,
        expenseStatementDO.declaredTotal(),
        expenseStatementDO.approvedTotal(),
        expenseStatementDO.pool(),
        expenseStatementDO.online(),
        expenseStatementDO.overBudget(),
        expenseStatementDO.refundable(),
        expenseStatementDO.evidenceCount(),
        time(expenseStatementDO.submittedAt()),
        time(expenseStatementDO.reviewedAt()),
        expenseStatementDO.reason(),
        refunds,
        List.copyOf(actions));
  }

  private record Locked(TradingRoots.Locked roots, RentalOrderDO order, TradingReceipt replay) {}

  /** 沿已有订单及资金根锁序重新读取命令执行依据。 */
  private Locked lock(TradingCommand command, String orderId) {
    RentalOrderDO rentalOrderDO =
        fulfillmentAccess.order(
            command.actor(),
            orderId,
            command.operation().permission() == null,
            command.operation().permission());
    TradingRoots.Locked locked = tradingRoots.lock(rentalOrderDO.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(command);
    if (tradingReceipt == null) fulfillmentProtection.requireNormal(orderId);
    return new Locked(locked, rentalOrderMapper.selectOrderById(orderId), tradingReceipt);
  }

  /** 拒绝修改已经被后续版本替换的申报。 */
  private ApiFailure stale() {
    return conflict(FulfillmentReason.EXPENSE_VERSION_NOT_CURRENT.name());
  }

  /** 同时校验最新申报身份与客户端版本。 */
  private ExpenseStatementDO current(String order, String id, int expected) {
    ExpenseStatementDO expenseStatementDO = expenseMapper.selectLatestExpenseStatement(order);
    if (expenseStatementDO == null || !expenseStatementDO.id().equals(id)) throw stale();
    if (expenseStatementDO.version() != expected) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return expenseStatementDO;
  }

  /** 保存草稿或以退回版本为依据建立新版本。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt save(TradingCommand command, String orderId, ExpenseDtos.Save save) {
    Locked locked = lock(command, orderId);
    if (locked.replay() != null) return locked.replay();
    if (!Set.of("accepted", "fulfilling", "return_checking").contains(locked.order().orderStatus()))
      throw conflict(FulfillmentReason.EXPENSE_NOT_READY.name());
    List<ExpenseMapper.Basis> legs = legs(orderId);
    if (legs.stream().noneMatch(l -> "freight_pool".equals(l.mode())))
      throw conflict(FulfillmentReason.EXPENSE_NOT_REQUIRED.name());
    ExpenseStatementDO expenseStatementDO = expenseMapper.selectLatestExpenseStatement(orderId);
    boolean editing = expenseStatementDO != null && "draft".equals(expenseStatementDO.status());
    if (expenseStatementDO != null && !editing && !"returned".equals(expenseStatementDO.status()))
      throw conflict(FulfillmentReason.EXPENSE_IMMUTABLE.name());
    if (!Objects.equals(save.previousStatementId(), expenseStatementDO != null && !editing ? expenseStatementDO.id() : null))
      throw stale();
    if (save.expectedVersion() != (expenseStatementDO == null ? 0 : expenseStatementDO.version()))
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    Set<String> reused = new TreeSet<>(save.reusedEvidenceIds());
    List<ExpenseMapper.Evidence> available =
        expenseStatementDO == null
            ? List.<ExpenseMapper.Evidence>of()
            : expenseMapper.selectExpenseEvidenceByOrderAndStatement(orderId, expenseStatementDO.id());
    Set<String> originals =
        editing
            ? new HashSet<>(
                objectMapper
                    .readValue(expenseStatementDO.detailJson(), ExpenseStatementDO.Details.class)
                    .reusedEvidenceIds())
            : null;
    List<ExpenseMapper.Evidence> refs =
        available.stream()
            .filter(e -> editing && expenseStatementDO.id().equals(e.statementId()) || reused.contains(e.id()))
            .toList();
    if (originals != null && !originals.containsAll(reused)
        || !refs.stream().map(ExpenseMapper.Evidence::id).toList().containsAll(reused))
      throw conflict(FulfillmentReason.MATERIAL_REFERENCE_INVALID.name());
    ExpenseRules.Checked checked = expenseRules.check(legs.get(0), save.outbound(), refs, false);
    ExpenseRules.Checked checkedReturnLeg = expenseRules.check(legs.get(1), save.returnLeg(), refs, false);
    String id = editing ? expenseStatementDO.id() : Ids.next();
    int number = expenseStatementDO == null ? 1 : expenseStatementDO.statementVersion() + (editing ? 0 : 1);
    ExpenseStatementDO.Details details =
        new ExpenseStatementDO.Details(1, checked.evidence(), checkedReturnLeg.evidence(), List.copyOf(reused));
    ExpenseMapper.Draft draft =
        new ExpenseMapper.Draft(
            id,
            orderId,
            locked.order().factoryId(),
            number,
            save.expectedVersion(),
            checked.amounts(),
            checkedReturnLeg.amounts(),
            ExpenseRules.amount((long) checked.amounts().total() + checkedReturnLeg.amounts().total()),
            legs.get(0).pool(),
            objectMapper.writeValueAsString(details),
            refs.size(),
            now(clock));
    if (editing) {
      if (expenseMapper.updateDraft(draft) != 1) throw stale();
    } else expenseMapper.insertDraft(draft);
    return tradingOperations.record(command, RESOURCE, id, editing ? expenseStatementDO.version() + 1 : 1);
  }

  /** 从冻结凭证和明细重建提交校验所需输入。 */
  private ExpenseDtos.Leg input(ExpenseViews.Leg leg, ExpenseStatementDO.LegEvidence evidence) {
    TransportExecutionSource source =
        leg.source() == null ? null : TransportExecutionSource.valueOf(leg.source());
    return new ExpenseDtos.Leg(
        source,
        leg.carrierName(),
        leg.carrierOrderNo(),
        evidence.baseProvided() ? Integer.valueOf(leg.baseAmountFen()) : null,
        leg.extraFees());
  }

  /** 重新校验两段计价、运输完成和凭证后提交。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt submit(TradingCommand command, String order, String id, int version) {
    Locked locked = lock(command, order);
    if (locked.replay() != null) return locked.replay();
    ExpenseStatementDO expenseStatementDO = current(order, id, version);
    if (!"draft".equals(expenseStatementDO.status())) throw conflict(FulfillmentReason.EXPENSE_IMMUTABLE.name());
    List<ExpenseMapper.Basis> legs = legs(order);
    if (legs.stream().anyMatch(l -> !"completed".equals(l.status())))
      throw conflict(FulfillmentReason.EXPENSE_NOT_READY.name());
    ExpenseStatementDO.Details details =
        objectMapper.readValue(expenseStatementDO.detailJson(), ExpenseStatementDO.Details.class);
    ExpenseViews.Expense display = view(expenseStatementDO, false, false);
    List<ExpenseMapper.Evidence> evidence = expenseMapper.selectExpenseEvidenceByOrderAndStatement(order, id);
    ExpenseRules.Checked checked =
        expenseRules.check(
            legs.get(0), input(display.outbound(), details.outbound()), evidence, true);
    ExpenseRules.Checked checkedReturnLeg =
        expenseRules.check(
            legs.get(1), input(display.returnLeg(), details.returnLeg()), evidence, true);
    ExpenseStatementDO.Details frozen =
        new ExpenseStatementDO.Details(1, checked.evidence(), checkedReturnLeg.evidence(), details.reusedEvidenceIds());
    if (expenseMapper.submitTransportExpenseStatement(id, version, objectMapper.writeValueAsString(frozen), now(clock)) != 1)
      throw stale();
    fulfillmentEvents.append(
        "expense:" + id + ":submitted",
        order,
        null,
        RESOURCE,
        id,
        FulfillmentEventType.transport_expense_submitted,
        now(clock),
        command.actor(),
        FulfillmentRole.factory,
        "工厂已提交往返费用申报");
    return tradingOperations.record(command, RESOURCE, id, version + 1);
  }

  /** 在原版本上互斥地批准或退回，批准后按原支付来源处理余额。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt review(
      TradingCommand command, String order, String id, ExpenseDtos.Review review) {
    Locked locked = lock(command, order);
    if (locked.replay() != null) return locked.replay();
    ExpenseStatementDO expenseStatementDO = current(order, id, review.expectedVersion());
    boolean approve = "approve".equals(review.decision());
    if (!approve && (review.reason() == null || review.reason().isBlank()))
      throw ApiFailure.invalid();
    if (!Set.of("submitted", "reviewing").contains(expenseStatementDO.status()))
      throw conflict(FulfillmentReason.EXPENSE_IMMUTABLE.name());
    java.time.LocalDateTime at = now(clock);
    if (expenseMapper.reviewTransportExpenseStatement(id, expenseStatementDO.version(), approve, command.actor(), review.reason(), at) != 1)
      throw stale();
    if (approve) {
      ExpenseStatementDO approved = expenseMapper.selectExpenseDetail(order, id);
      ExpenseMapper.LegWrite legWrite =
          new ExpenseMapper.LegWrite(
              expenseStatementDO.outboundId(),
              expenseStatementDO.outboundSource(),
              expenseStatementDO.outboundCarrier(),
              expenseStatementDO.outboundOrderNo(),
              expenseStatementDO.outboundBase(),
              expenseStatementDO.outboundTotal() - expenseStatementDO.outboundBase(),
              expenseStatementDO.outboundTotal());
      ExpenseMapper.LegWrite returnLegWrite =
          new ExpenseMapper.LegWrite(
              expenseStatementDO.returnId(),
              expenseStatementDO.returnSource(),
              expenseStatementDO.returnCarrier(),
              expenseStatementDO.returnOrderNo(),
              expenseStatementDO.returnBase(),
              expenseStatementDO.returnTotal() - expenseStatementDO.returnBase(),
              expenseStatementDO.returnTotal());
      if (expenseMapper.approveLeg(order, legWrite, at) != 1
          || expenseMapper.approveLeg(order, returnLegWrite, at) != 1
          || expenseMapper.approveAccount(approved, at) != 1)
        throw conflict(FulfillmentReason.EXPENSE_NOT_READY.name());
      normalOrderFunds.freight(
          locked.roots(), locked.order(), id, command.actor(), approved.refundable());
      normalOrderFunds.reconcile(order);
    }
    fulfillmentEvents.append(
        "expense:" + id + ":" + review.decision(),
        order,
        null,
        RESOURCE,
        id,
        approve
            ? FulfillmentEventType.transport_expense_approved
            : FulfillmentEventType.transport_expense_returned,
        at,
        command.actor(),
        FulfillmentRole.admin,
        approve ? "往返费用已按申报原额核准" : "费用申报已退回补正");
    return tradingOperations.recordReason(command, RESOURCE, id, expenseStatementDO.version() + 1, review.reason());
  }
}
