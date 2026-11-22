package com.mujian.backend.delivery.service;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.conflict;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.delivery.domain.ExpenseStatementDO.LegEvidence;
import com.mujian.backend.delivery.domain.PriceRuleDO;
import com.mujian.backend.delivery.dto.ExpenseDtos;
import com.mujian.backend.delivery.dto.TransportDtos;
import com.mujian.backend.delivery.mapper.ExpenseMapper;
import com.mujian.backend.delivery.policy.TransportCalculation;
import com.mujian.backend.order.domain.CheckoutQuoteData;
import com.mujian.backend.order.service.OrderFormation;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 价格、运费分摊及关联照片全部按本单冻结的运输段核算。 */
@Component
public class ExpenseRules {
  public record Checked(ExpenseMapper.LegWrite amounts, LegEvidence evidence) {}

  private final ObjectMapper objectMapper;

  /** 装配原运价快照解析所需依赖。 */
  public ExpenseRules(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 只按原下单运价和路线快照确定自有车价格。 */
  public Integer ownPrice(String mode, String snapshot) {
    if (!"freight_pool".equals(mode)) return null;
    OrderFormation.LegSnapshot data =
        objectMapper.readValue(snapshot, OrderFormation.LegSnapshot.class);
    CheckoutQuoteData.Leg leg = data.leg();
    if (data.schemaVersion() != 1
        || leg.rule() == null
        || leg.route() == null
        || !mode.equals(leg.chargeMode()))
      throw new ApiFailure(
          500, D2Codes.CODE_DATA_INTEGRITY_ERROR, FulfillmentReason.FULFILLMENT_DATA_INCONSISTENT.name());
    PriceRuleDO rule = leg.rule();
    return TransportCalculation.price(
            leg.route().distanceMeter(),
            rule.includedDistanceMeter(),
            rule.guideMinAmountFen(),
            rule.guideMaxAmountFen(),
            rule.ownVehicleSettleAmountFen(),
            rule.extraKmAmountFen())
        .ownVehicleFen();
  }

  /** 检查分值非负且能写入单笔整数金额。 */
  public static int amount(long value) {
    if (value < 0 || value > Integer.MAX_VALUE) throw ApiFailure.invalid();
    return (int) value;
  }

  /** 草稿保存未填的基础金额，提交时区分明确零收费与漏填。 */
  public Checked check(
      ExpenseMapper.Basis basis,
      ExpenseDtos.Leg leg,
      List<ExpenseMapper.Evidence> objects,
      boolean submitting) {
    boolean pooled = "freight_pool".equals(basis.mode());
    TransportExecutionSource source = leg.source();
    boolean own = source == TransportExecutionSource.factory_vehicle;
    if (!pooled
        && (source != null
            || leg.baseAmountFen() != null
            || leg.carrierName() != null
            || leg.carrierOrderNo() != null
            || !leg.extraFees().isEmpty())) throw ApiFailure.invalid();
    if (own
        && (leg.baseAmountFen() != null
            || leg.carrierName() != null
            || leg.carrierOrderNo() != null)) throw ApiFailure.invalid();
    Set<String> all = new TreeSet<>(), baseIds = new TreeSet<>();
    for (ExpenseMapper.Evidence photo : objects)
      if (basis.id().equals(photo.legId())) {
        all.add(photo.id());
        if (!"extra_fee".equals(photo.scene())) baseIds.add(photo.id());
      }
    Map<String, Integer> ceilings = new HashMap<>();
    Map<String, Long> claimed = new HashMap<>();
    if (!leg.extraFees().isEmpty()) {
      PriceRuleDO rule =
          objectMapper.readValue(basis.snapshot(), OrderFormation.LegSnapshot.class).leg().rule();
      for (TransportDtos.ExtraFee allowed :
          objectMapper.readValue(
              rule.allowedExtraFeeJson(),
              com.mujian.backend.delivery.dto.TransportDtos.ExtraFee[].class))
        ceilings.put(allowed.type(), allowed.maxAmountFen());
    }
    long extras = 0;
    for (ExpenseDtos.ExtraFee extra : leg.extraFees()) {
      if (!all.containsAll(extra.evidenceIds())
          || new HashSet<>(extra.evidenceIds()).size() != extra.evidenceIds().size())
        throw conflict(FulfillmentReason.MATERIAL_REFERENCE_INVALID.name());
      if (submitting && extra.evidenceIds().isEmpty())
        throw conflict(FulfillmentReason.EXPENSE_EVIDENCE_REQUIRED.name());
      long byType = claimed.merge(extra.type(), (long) extra.amountFen(), Math::addExact);
      if (byType > ceilings.getOrDefault(extra.type(), 0)) throw ApiFailure.invalid();
      extras += extra.amountFen();
      baseIds.removeAll(extra.evidenceIds());
    }
    if (submitting
        && pooled
        && (source == null
            || !own
                && (leg.baseAmountFen() == null
                    || leg.carrierName() == null
                    || leg.carrierOrderNo() == null)))
      throw conflict(FulfillmentReason.EXPENSE_NOT_READY.name());
    if (submitting && source == TransportExecutionSource.third_party && baseIds.isEmpty())
      throw conflict(FulfillmentReason.EXPENSE_EVIDENCE_REQUIRED.name());
    int base =
        own
            ? ownPrice(basis.mode(), basis.snapshot())
            : leg.baseAmountFen() == null ? 0 : leg.baseAmountFen();
    return new Checked(
        new ExpenseMapper.LegWrite(
            basis.id(),
            source == null ? null : source.name(),
            leg.carrierName(),
            leg.carrierOrderNo(),
            base,
            amount(extras),
            amount(base + extras)),
        new LegEvidence(List.copyOf(baseIds), leg.extraFees(), leg.baseAmountFen() != null));
  }
}
