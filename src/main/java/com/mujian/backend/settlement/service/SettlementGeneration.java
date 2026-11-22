package com.mujian.backend.settlement.service;

import static com.mujian.backend.common.support.D2Values.now;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.settlement.mapper.SettlementMapper;
import com.mujian.backend.settlement.mapper.SettlementMapper.*;
import com.mujian.backend.settlement.policy.SettlementAmounts;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 同一工厂周期一次固化，所有来源先核对原账，失败不留下半个批次。 */
@Service
public class SettlementGeneration {
  private static final Logger LOG = LoggerFactory.getLogger(SettlementGeneration.class);
  private final SettlementMapper settlementMapper;
  private final FactoryAccess factoryAccess;
  private final FulfillmentProtection fulfillmentProtection;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  /** 装配结算来源核对及批次固化所需依赖。 */
  public SettlementGeneration(
      SettlementMapper settlementMapper,
      FactoryAccess factoryAccess,
      FulfillmentProtection fulfillmentProtection,
      Clock clock,
      ObjectMapper objectMapper) {
    this.settlementMapper = settlementMapper;
    this.factoryAccess = factoryAccess;
    this.fulfillmentProtection = fulfillmentProtection;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  public record FrozenSource(
      String paymentOrderId,
      String suborderId,
      String allocationId,
      String scene,
      int cashRentFen,
      int cashDepositFen,
      int cashFreightFen,
      int compensationFen,
      List<Refund> refunds,
      int rentBaseFen,
      int commissionFen,
      int factoryUnlockFen) {}

  public record Frozen(int schemaVersion, List<FrozenSource> sources, List<Basis> adjustments) {}

  private record Prepared(Detail detail, List<Chain> chains) {}

  /** 按上海每日两点确定已结束的业务期间。 */
  public static LocalDateTime cutoff(LocalDateTime at) {
    return (at.getHour() < 2 ? at.toLocalDate().minusDays(1) : at.toLocalDate()).atStartOfDay();
  }

  /** 锁定并复查合格来源后一次固化本厂当期批次。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void generate(String factory) {
    LocalDateTime at = now(clock), end = cutoff(at);
    List<Candidate> hints = settlementMapper.selectRentalOrderCandidates(factory, end);
    List<CustomCandidate> customHints=settlementMapper.customCandidates(factory,end);
    if (hints.isEmpty() && customHints.isEmpty() || settlementMapper.selectSettlementFrozen(factory, end)) return;
    Set<String> users = new TreeSet<>(), ids = new HashSet<>();
    for (Candidate settlementCandidate : hints) {
      users.add(settlementCandidate.buyer());
      users.add(settlementCandidate.owner());
      ids.add(settlementCandidate.id());
    }
    Set<String> customIds=new HashSet<>();
    for(SettlementMapper.CustomCandidate customCandidate:customHints){users.add(customCandidate.buyer());users.add(customCandidate.owner());customIds.add(customCandidate.id());}
    factoryAccess.lock(users.toArray(String[]::new));
    settlementMapper.lockFactory(factory);
    if (settlementMapper.selectSettlementFrozen(factory, end)) return;
    for (Candidate candidateToLock : hints) settlementMapper.lockOrder(candidateToLock.id());
    List<Prepared> orders = new ArrayList<>();
    long rent = 0, fee = 0, freight = 0, payable = 0, depositCompensation=0;
    for (Candidate rentalCandidate : settlementMapper.selectRentalOrderCandidates(factory, end)) {
      if (!ids.contains(rentalCandidate.id())
          || !fulfillmentProtection.canSettle(rentalCandidate.id())) continue;
      try {
        Prepared prepared = prepare(rentalCandidate);
        if (prepared.chains().isEmpty()) continue;
        orders.add(prepared);
        Detail settlementDetail = prepared.detail();
        rent = Math.addExact(rent, settlementDetail.base());
        fee = Math.addExact(fee, settlementDetail.commission());
        freight = Math.addExact(freight, settlementDetail.reimbursement());
        depositCompensation=Math.addExact(depositCompensation,settlementDetail.depositCompensation());
        payable = Math.addExact(payable, settlementDetail.unlock());
      } catch (UnsupportedSource e) {
        LOG.warn("结算来源待核对 orderId={} reason={}", rentalCandidate.id(), e.getMessage());
      }
    }
    for(SettlementMapper.CustomCandidate customCandidateToLock:customHints)settlementMapper.lockCustomOrder(customCandidateToLock.id());
    List<CustomDetail> custom=new ArrayList<>();long goodsBase=0;
    for(SettlementMapper.CustomCandidate customCandidate:settlementMapper.customCandidates(factory,end)){
      if(!customIds.contains(customCandidate.id()))continue;
      Source source=settlementMapper.customSource(customCandidate.id());
      if(source==null || !Set.of("paid_frozen","partially_refunded").contains(source.state()) || source.transactionId()==null
          || source.accountName()==null || source.total()!=source.paid() || (long)customCandidate.goods()+customCandidate.freight()!=source.total() || customCandidate.goods()!=source.other() || customCandidate.freight()!=source.cashFreightFen())continue;
      Returns refund=settlementMapper.customRefunds(source.suborderId());
      if((long)refund.goods()+refund.freight()!=source.refunded() || source.refunded()!=customCandidate.refunded() || refund.goods()>customCandidate.goods() || refund.freight()>customCandidate.freight())continue;
      int base=customCandidate.goods()-refund.goods(),netFreight=customCandidate.freight()-refund.freight(),commission=Math.toIntExact((long)base*customCandidate.rate()/10000);
      Chain chain=new Chain(Ids.next(),Ids.next(),Ids.next(),Ids.next(),customCandidate.id(),source,commission,Math.addExact(base-commission,netFreight));
      custom.add(new CustomDetail(customCandidate,refund,base,netFreight,chain));
      goodsBase+=base;fee+=commission;freight+=netFreight;payable+=chain.unlock();
    }
    if(orders.isEmpty() && custom.isEmpty())return;
    Batch settlementBatch =
        new Batch(
            Ids.next(),
            factory,
            end.minusDays(1),
            end,
            at,
            orders.size()+custom.size(),
            rent,
            fee,
            freight,
            payable,depositCompensation,goodsBase);
    settlementMapper.insertBatch(settlementBatch);
    for (Prepared preparedOrder : orders) {
      settlementMapper.insertDetail(settlementBatch, preparedOrder.detail());
      settlementMapper.insertCommission(Ids.next(), settlementBatch, preparedOrder.detail());
      for (Chain chain : preparedOrder.chains()) {
        if (settlementMapper.updatePaymentChannelSuborderReserve(chain, settlementBatch) != 1)
          throw new IllegalStateException("settlement source changed");
        settlementMapper.insertChain(settlementBatch, chain);
      }
    }
    for(SettlementMapper.CustomDetail customDetail:custom){
      settlementMapper.insertCustomDetail(settlementBatch,customDetail);
      settlementMapper.insertCustomCommission(Ids.next(),settlementBatch,customDetail);
      if(settlementMapper.updatePaymentChannelSuborderReserve(customDetail.chain(),settlementBatch)!=1)throw new IllegalStateException("定制结算来源已变化");
      settlementMapper.insertChain(settlementBatch,customDetail.chain());
    }
    settlementMapper.insertAttempt(Ids.next(),settlementBatch,orders.isEmpty()?custom.get(0).chain().source():orders.get(0).chains().get(0).source());
  }

  private static class UnsupportedSource extends RuntimeException {
    UnsupportedSource(String reason) {
      super(reason);
    }
  }

  /** 账户依据不完整时排除该订单并保留异常原因。 */
  private static void check(boolean valid, String reason) {
    if (!valid) throw new UnsupportedSource(reason);
  }

  /** 逐个核对原分摊和退款依据，计算可执行的来源金额。 */
  private Prepared prepare(Candidate c) {
    List<Source> sources = settlementMapper.selectSettlementSourcesByOrder(c.id());
    check(!sources.isEmpty(), "MISSING_SOURCE");
    List<Basis> basis = c.incremental()?List.of():settlementMapper.selectOrderPriceAdjustmentBasis(c.id());
    int discount = 0, cancellation = 0, depositCompensation=0, extension=0, supplement=0,rentOffset=0;
    for (Basis settlementBasis : basis) {
      if ("coupon".equals(settlementBasis.kind()) && "rent".equals(settlementBasis.component()))
        discount = Math.addExact(discount, settlementBasis.amount());
      else if ("cancellation_fee".equals(settlementBasis.kind()) && "rent".equals(settlementBasis.component()))
        cancellation = Math.addExact(cancellation, settlementBasis.amount());
      else if ("deposit".equals(settlementBasis.component()) && "overdue_rent".equals(settlementBasis.kind()))
        rentOffset=Math.addExact(rentOffset,settlementBasis.amount());
      else if ("deposit".equals(settlementBasis.component()) && Set.of("overdue_liability","claim","arbitration","platform_adjustment").contains(settlementBasis.kind()))
        depositCompensation=Math.addExact(depositCompensation,settlementBasis.amount());
      else if ("rent".equals(settlementBasis.component()) && "arbitration".equals(settlementBasis.kind()))
        cancellation=Math.addExact(cancellation,settlementBasis.amount());
      else if (!("credit_waiver".equals(settlementBasis.kind()) && "deposit".equals(settlementBasis.component())))
        throw new UnsupportedSource("UNSUPPORTED_ADJUSTMENT");
    }
    Map<String, List<Refund>> refunds = new HashMap<>();
    for (Refund settlementRefund : settlementMapper.selectRefundsForSettlement(c.id())) {
      List<Refund> suborderRefunds = refunds.get(settlementRefund.suborderId());
      if (suborderRefunds == null) {
        suborderRefunds = new ArrayList<>();
        refunds.put(settlementRefund.suborderId(), suborderRefunds);
      }
      suborderRefunds.add(settlementRefund);
    }
    List<SettlementAmounts.Cash> cash = new ArrayList<>();
    int retainedDeposit=0;
    int rent = 0, deposit = 0, freight = 0, coupon = 0, rentReturned = 0, freightReturned = 0;
    for (Source settlementSource : sources) {
      check(
          settlementSource.other() == 0
              && settlementSource.transactionId() != null
              && settlementSource.merchant() != null
              && settlementSource.accountName() != null
              && Set.of("initial_order","extension_rent","compensation_supplement").contains(settlementSource.scene())
              && (!c.incremental() || "compensation_supplement".equals(settlementSource.scene()))
              && ("compensation_supplement".equals(settlementSource.scene()) ? (long)settlementSource.compensation()+settlementSource.cashRentFen()==settlementSource.paid() : settlementSource.compensation()==0)
              && Set.of("paid_frozen", "partially_refunded", "fully_refunded").contains(settlementSource.state()),
          "UNSUPPORTED_SOURCE");
      int rr = 0, dr = 0, fr = 0;
      for (Refund sourceRefund : refunds.getOrDefault(settlementSource.suborderId(), List.of())) {
        check(
            sourceRefund.other() == 0 && (long) sourceRefund.rent() + sourceRefund.deposit() + sourceRefund.freight() == sourceRefund.amount(),
            "UNKNOWN_REFUND");
        rr = Math.addExact(rr, sourceRefund.rent());
        dr = Math.addExact(dr, sourceRefund.deposit());
        fr = Math.addExact(fr, sourceRefund.freight());
      }
      check(
          (long) rr + dr + fr == settlementSource.refunded()
              && dr <= settlementSource.cashDepositFen()
              && rr <= settlementSource.cashRentFen()
              && fr <= settlementSource.cashFreightFen(),
          "SOURCE_REFUND_MISMATCH");
      check(
          (long) settlementSource.cashRentFen() + settlementSource.cashDepositFen() + settlementSource.cashFreightFen() + settlementSource.compensation() == settlementSource.total()
              && settlementSource.total() == settlementSource.paid(),
          "SOURCE_CASH_MISMATCH");
      retainedDeposit=Math.addExact(retainedDeposit,settlementSource.cashDepositFen()-dr);
      supplement=Math.addExact(supplement,settlementSource.compensation());
      if("extension_rent".equals(settlementSource.scene()))extension=Math.addExact(extension,settlementSource.cashRentFen());
      int originalRent="compensation_supplement".equals(settlementSource.scene())?0:settlementSource.cashRentFen();
      rent = Math.addExact(rent, originalRent);
      deposit = Math.addExact(deposit, settlementSource.cashDepositFen());
      freight = Math.addExact(freight, settlementSource.cashFreightFen());
      coupon = Math.addExact(coupon, settlementSource.coupon());
      rentReturned = Math.addExact(rentReturned, rr);
      freightReturned = Math.addExact(freightReturned, fr);
      cash.add(
          new SettlementAmounts.Cash(
              settlementSource.suborderId(), originalRent - rr, settlementSource.cashFreightFen() - fr));
    }
    check(retainedDeposit==(long)depositCompensation+rentOffset && (c.incremental() || retainedDeposit==c.depositDeducted()),"DEPOSIT_BASIS_MISMATCH");
    if(!c.incremental())check(
        rent == c.rent()
            && deposit == c.deposit()
            && freight == c.freight()
            && coupon == discount
            && rentReturned == c.rentReturned()
            && freightReturned == c.freightReturned()
            && freight - freightReturned == c.reimbursement()
            && (c.terminated() || c.reimbursement() == Math.min(c.freight(), c.approved()))
            && cancellation == c.cancellation(),
        "ORDER_BALANCE_MISMATCH");
    int base = rent - rentReturned;
    check(
        c.incremental() || c.terminated() || "completed".equals(c.status()) || base == cancellation,
        "CANCELLATION_MISMATCH");
    List<SettlementAmounts.Split> splits = SettlementAmounts.split(cash);
    int priorOverdue=c.incremental()?settlementMapper.frozenOverdue(c.id()):0;
    int overdue=0, overdueCommission=0;
    int fee = 0, unlock = 0;
    String detailId = Ids.next();
    List<Chain> chains = new ArrayList<>();
    List<FrozenSource> frozen = new ArrayList<>();
    for (int i = 0; i < sources.size(); i++) {
      Source fundingSource = sources.get(i);
      SettlementAmounts.Split split = splits.get(i);
      check(fundingSource.suborderId().equals(split.suborderId()), "SOURCE_ORDER_MISMATCH");
      int refundedDeposit=refunds.getOrDefault(fundingSource.suborderId(),List.of()).stream().mapToInt(Refund::deposit).sum();
      int supplementalRent="compensation_supplement".equals(fundingSource.scene())?fundingSource.cashRentFen():0;
      int currentOverdue="initial_order".equals(fundingSource.scene())?rentOffset:supplementalRent;
      int cumulative=Math.addExact(priorOverdue,overdue);
      int extraCommission=Math.addExact(cumulative,currentOverdue)/5-cumulative/5;
      overdue=Math.addExact(overdue,currentOverdue);
      overdueCommission=Math.addExact(overdueCommission,extraCommission);
      int commission=Math.addExact(split.commissionFen(),extraCommission);
      int remaining=Math.toIntExact((long)split.factoryUnlockFen()+fundingSource.cashDepositFen()-refundedDeposit+fundingSource.compensation()+supplementalRent-extraCommission);
      check((long) commission + remaining == fundingSource.paid() - fundingSource.refunded(), "UNASSIGNED_CASH");
      fee = Math.addExact(fee, commission);
      unlock = Math.addExact(unlock, remaining);
      frozen.add(
          new FrozenSource(
              fundingSource.paymentOrderId(),
              fundingSource.suborderId(),
              fundingSource.allocationId(),
              fundingSource.scene(),
              fundingSource.cashRentFen(),
              fundingSource.cashDepositFen(),
              fundingSource.cashFreightFen(),fundingSource.compensation(),
              refunds.getOrDefault(fundingSource.suborderId(), List.of()),
              Math.addExact(cash.get(i).rentBaseFen(),currentOverdue),
              commission,
              remaining));
      if (commission > 0 || remaining > 0)
        chains.add(
            new Chain(
                Ids.next(),
                commission == 0 ? null : Ids.next(),
                Ids.next(),
                detailId,
                c.id(),
                fundingSource,
                commission,
                remaining));
    }
    return new Prepared(
        new Detail(
            detailId,
            c.id(),
            sources.get(0).paymentOrderId(),
            objectMapper.writeValueAsString(new Frozen(1, frozen, basis)),
            Math.addExact(rent,overdue),
            coupon,
            rentReturned,
            cancellation,
            Math.addExact(base,overdue),
            fee,
            c.incremental()?0:c.approved(),
            freightReturned,
            c.incremental()?0:c.reimbursement(),
            unlock,c.incremental()?"rental:"+c.id()+":supplement:"+sources.get(0).suborderId():"rental:"+c.id()+":normal",
            extension,depositCompensation,supplement,overdue,overdueCommission,rentOffset),
        chains);
  }
}
