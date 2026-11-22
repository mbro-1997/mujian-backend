package com.mujian.backend.delivery.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.OverdueFeeStatus;
import com.mujian.backend.common.enums.OverdueReviewBand;
import com.mujian.backend.common.enums.ReturnHandoverMethod;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.mapper.OverdueFeeMapper;
import com.mujian.backend.delivery.mapper.OverdueFeeMapper.*;
import com.mujian.backend.dispute.mapper.DisputeFundsMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 以同一订单根锁串行核定、退押和扣押。读预估永远不生成资金记录。 */
@Service
public class OverdueFees {
  public record Quantity(String itemId, int quantity) {}

  public record Review(
      int expectedVersion,
      String handoverMethod,
      String handoverAt,
      long excludedMs,
      List<Quantity> items,
      int liabilityFen,
      boolean unreportedBuyerFault,
      String basis) {}

  public record Pickup(int expectedVersion, String handoverAt, String basis, boolean disputed) {}

  public record View(
      String status,
      int version,
      String dueDate,
      String handoverMethod,
      String handoverAt,
      String handoverBasis,
      boolean disputed,
      Long elapsedMs,
      Long excludedMs,
      Long chargeableMs,
      Integer days,
      List<Line> lines,
      Integer rentFen,
      Integer capFen,
      Integer liabilityFen,
      Integer rentOffsetFen,
      Integer liabilityOffsetFen,
      Integer rentDueFen,
      Integer liabilityDueFen,
      int rentPaidFen,
      int liabilityPaidFen,
      int availableDepositFen,
      String reviewBasis,
      String receivableId) {}

  private final OverdueFeeMapper overdueFeeMapper;
  private final DisputeFundsMapper disputeFundsMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配费用来源、押金余额与业务时钟。 */
  public OverdueFees(
      OverdueFeeMapper overdueFeeMapper,
      DisputeFundsMapper disputeFundsMapper,
      ObjectMapper objectMapper,
      Clock clock) {
    this.overdueFeeMapper = overdueFeeMapper;
    this.disputeFundsMapper = disputeFundsMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  static final long DAY = Duration.ofDays(1).toMillis();

  /** 上海有效归还日次日零点为计费起点。 */
  static long elapsed(LocalDate due, LocalDateTime at) {
    return Math.max(0, Duration.between(due.plusDays(1).atStartOfDay(), at).toMillis());
  }

  /** 只将应由买家承担的正时长进位到整天。 */
  static int days(long ms) {
    return ms == 0 ? 0 : Math.toIntExact(1 + (ms - 1) / DAY);
  }

  /** 按实际时长确定额外扣款上限，金额向下到分。 */
  static int cap(long ms, int deposit) {
    int percent = OverdueReviewBand.forDuration(Duration.ofMillis(ms)).ceilingPercent();
    return (int) ((long) deposit * percent / 100);
  }

  /** 按各项订单单价和核定数量计算到分。 */
  static int rent(List<Line> lines, int days) {
    long total = 0;
    for (Line line : lines) {
      long daily = (long) line.dailyRentFen() * line.quantity();
      if (days > 0 && daily > (Integer.MAX_VALUE - total) / days) throw ApiFailure.invalid();
      total += daily * days;
    }
    return (int) total;
  }

  /** 历史已收尾订单不追溯收费。 */
  private static boolean applicable(Facts facts) {
    return facts != null
        && (facts.feeId() != null
            || facts.returning() && !facts.closed() && facts.earliest() != null);
  }

  /** 兼容有运单号的旧寄送事实。 */
  private static String method(Facts facts) {
    return facts.method() != null ? facts.method() : facts.legacyMethod();
  }

  /** 核定时间优先，其次读取有凭据的原始寄送时间。 */
  private static LocalDateTime at(Facts facts) {
    return facts.handoverAt() != null
        ? facts.handoverAt()
        : facts.legacyMethod() != null ? facts.legacyAt() : null;
  }

  /** 保留参与方最初填写的交接依据。 */
  private static String basis(Facts facts) {
    return facts.basis() != null ? facts.basis() : facts.legacyBasis();
  }

  /** 按当前费用版本返回预估或已生效金额及实际补收。 */
  public View read(String order) {
    Facts facts = overdueFeeMapper.selectFeeFactsByOrder(order);
    if (!applicable(facts)) return null;
    LocalDateTime at = at(facts);
    Long elapsed = at == null ? null : elapsed(facts.dueDate(), at);
    Long charge =
        elapsed == null ? null : elapsed - Objects.requireNonNullElse(facts.excludedMs(), 0L);
    Integer days = charge == null ? null : days(charge);
    List<Line> lines =
        facts.snapshot() == null
            ? overdueFeeMapper.selectPriceLinesByOrder(order)
            : List.of(objectMapper.readValue(facts.snapshot(), Line[].class));
    String status =
        facts.status() != null
            ? facts.status()
            : at == null
                ? OverdueFeeStatus.awaiting_handover.name()
                : OverdueFeeStatus.pending_review.name();
    Integer rentFen = facts.rent();
    if (rentFen == null && days != null) rentFen = rent(lines, days);
    return new View(
        status,
        facts.version(),
        facts.dueDate().toString(),
        method(facts),
        time(at),
        basis(facts),
        facts.disputed(),
        elapsed,
        facts.excludedMs(),
        charge,
        days,
        lines,
        rentFen,
        charge == null ? null : cap(charge, facts.deposit()),
        facts.liability(),
        facts.rentOffset(),
        facts.liabilityOffset(),
        facts.rent() == null ? null : facts.rent() - facts.rentOffset(),
        facts.liability() == null ? null : facts.liability() - facts.liabilityOffset(),
        facts.rentPaid(),
        facts.liabilityPaid(),
        disputeFundsMapper.selectAvailableOrderFundsBySubject(order, "deposit"),
        facts.reviewBasis(),
        facts.receivableId());
  }

  /** 已到核验环节但缺交接凭据时，保留待核来源供实物收尾后续办。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void trackLateReturn(String orderId) {
    Facts facts = overdueFeeMapper.selectFeeFactsByOrder(orderId);
    if (!applicable(facts) || facts.feeId() != null) return;
    LocalDateTime handover = at(facts);
    if (handover == null || elapsed(facts.dueDate(), handover) > 0) {
      OverdueFeeStatus overdueFeeStatus =
          handover == null ? OverdueFeeStatus.awaiting_handover : OverdueFeeStatus.pending_review;
      overdueFeeMapper.insertFeeSource(
          Ids.next(), orderId, facts.dueDate(), overdueFeeStatus.name(), now(clock));
    }
  }

  /** 旧订单只在仍有归还责任时参与。实际到厂能证明此前已交接，但不能据此推算收费天数。 */
  public void requireResolved(String order) {
    Facts facts = overdueFeeMapper.selectFeeFactsByOrder(order);
    if (!applicable(facts) || OverdueFeeStatus.effective.name().equals(facts.status())) return;
    LocalDateTime latest = at(facts);
    if (latest == null) latest = facts.arrival();
    if (facts.disputed() || latest == null || elapsed(facts.dueDate(), latest) > 0)
      throw conflict(com.mujian.backend.common.enums.FulfillmentReason.OVERDUE_FEE_PENDING.name());
  }

  /** 调用者持有订单根锁，仅首次为该订单创建来源。 */
  private Facts ensure(Facts facts) {
    if (facts.feeId() != null) return facts;
    overdueFeeMapper.insertFeeSource(
        Ids.next(),
        facts.orderId(),
        facts.dueDate(),
        OverdueFeeStatus.awaiting_handover.name(),
        now(clock));
    return overdueFeeMapper.selectFeeFactsByOrder(facts.orderId());
  }

  /** 核对交接时间、实际运输方式和已有的回程派车事实。 */
  private void checkTime(Facts facts, String method, LocalDateTime handover) {
    if (handover == null
        || facts.earliest() == null
        || handover.isBefore(facts.earliest())
        || handover.isAfter(now(clock))
        || facts.arrival() != null && handover.isAfter(facts.arrival())
        || (ReturnHandoverMethod.factory_pickup.name().equals(method)
            != ReturnHandoverMethod.factory_pickup.name().equals(facts.mode()))
        || !ReturnHandoverMethod.supports(method)) throw ApiFailure.invalid();
    if (facts.dispatch() != null && handover.isBefore(facts.dispatch()))
      throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
  }

  /** 发出和实物接走使用原始业务时刻。派车本身不记录为交接。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void report(
      String order,
      String method,
      LocalDateTime handover,
      String evidence,
      boolean disputed,
      String actor) {
    Facts facts = ensure(overdueFeeMapper.selectFeeFactsByOrder(order));
    checkTime(facts, method, handover);
    if (evidence == null || evidence.isBlank()) throw ApiFailure.invalid();
    if (overdueFeeMapper.recordReportedHandover(
            order, method, handover, evidence, actor, disputed, now(clock))
        != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (!disputed && elapsed(facts.dueDate(), handover) == 0) {
      Facts current = overdueFeeMapper.selectFeeFactsByOrder(order);
      finish(
          current,
          method,
          handover,
          0,
          overdueFeeMapper.selectPriceLinesByOrder(order),
          0,
          false,
          evidence,
          actor);
    }
  }

  /** 工厂在派车后补录真正接走货物的时刻。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public int pickup(String order, Pickup pickup, String actor) {
    Facts facts = overdueFeeMapper.selectFeeFactsByOrder(order);
    LocalDateTime at = ReturnShipments.parseTime(pickup.handoverAt());
    if (!applicable(facts) || facts.dispatch() == null || facts.closed())
      throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    if (facts.version() != pickup.expectedVersion()) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    report(
        order,
        ReturnHandoverMethod.factory_pickup.name(),
        at,
        pickup.basis(),
        pickup.disputed(),
        actor);
    return overdueFeeMapper.selectFeeFactsByOrder(order).version();
  }

  /** 平台依据凭证逐项核实逾期数量、时长和责任扣款。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public int assess(String order, Review review, String actor) {
    Facts facts = overdueFeeMapper.selectFeeFactsByOrder(order);
    if (!applicable(facts) || OverdueFeeStatus.effective.name().equals(facts.status()))
      throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    if (facts.version() != review.expectedVersion()) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    LocalDateTime at = ReturnShipments.parseTime(review.handoverAt());
    checkTime(facts, review.handoverMethod(), at);
    Map<String, Integer> quantities = new HashMap<String, Integer>();
    for (Quantity quantity : review.items())
      if (quantities.put(quantity.itemId(), quantity.quantity()) != null)
        throw ApiFailure.invalid();
    List<Line> lines = new ArrayList<Line>();
    for (Line line : overdueFeeMapper.selectPriceLinesByOrder(order)) {
      Integer quantity = quantities.remove(line.itemId());
      if (quantity == null || quantity < 0 || quantity > line.quantity())
        throw ApiFailure.invalid();
      lines.add(new Line(line.itemId(), quantity, line.dailyRentFen()));
    }
    if (!quantities.isEmpty() || lines.isEmpty() || review.basis().isBlank())
      throw ApiFailure.invalid();
    finish(
        ensure(facts),
        review.handoverMethod(),
        at,
        review.excludedMs(),
        lines,
        review.liabilityFen(),
        review.unreportedBuyerFault(),
        review.basis(),
        actor);
    return facts.version() + 1;
  }

  /** 同一事务固化审核、先抵租金，再抵责任款并登记差额。 */
  private void finish(
      Facts facts,
      String method,
      LocalDateTime at,
      long excluded,
      List<Line> lines,
      int liability,
      boolean fault,
      String basis,
      String actor) {
    long ms = elapsed(facts.dueDate(), at) - excluded;
    if (excluded < 0
        || ms < 0
        || liability < 0
        || liability > cap(ms, facts.deposit())
        || liability > 0 && (!fault || lines.stream().noneMatch(line -> line.quantity() > 0)))
      throw ApiFailure.invalid();
    int days = days(ms),
        rent = rent(lines, days),
        available = disputeFundsMapper.selectAvailableOrderFundsBySubject(facts.orderId(), "deposit");
    int rentOffset = Math.min(rent, available),
        liabilityOffset = Math.min(liability, available - rentOffset);
    long gap = (long) rent - rentOffset + liability - liabilityOffset;
    if (gap > Integer.MAX_VALUE) throw ApiFailure.invalid();
    LocalDateTime now = now(clock);
    if (overdueFeeMapper.confirmFeeAssessment(
            facts.orderId(),
            facts.version(),
            method,
            at,
            excluded,
            days,
            objectMapper.writeValueAsString(lines),
            rent,
            liability,
            rentOffset,
            liabilityOffset,
            basis,
            actor,
            fault,
            now)
        != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (rentOffset > 0)
      overdueFeeMapper.insertDepositOffset(
          Ids.next(),
          facts.orderId(),
          facts.feeId(),
          "overdue_rent",
          rentOffset,
          basis,
          actor,
          now);
    if (liabilityOffset > 0)
      overdueFeeMapper.insertDepositOffset(
          Ids.next(),
          facts.orderId(),
          facts.feeId(),
          "overdue_liability",
          liabilityOffset,
          basis,
          actor,
          now);
    if (rentOffset + liabilityOffset > 0) disputeFundsMapper.updateRentalOrderTotals(facts.orderId(), now);
    if (gap > 0) overdueFeeMapper.insertGapReceivable(Ids.next(), facts.orderId(), available, now);
  }
}
