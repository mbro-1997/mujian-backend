package com.mujian.backend.coupon.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.coupon.mapper.CouponMapper;
import com.mujian.backend.coupon.policy.CouponAllocation;
import com.mujian.backend.coupon.projection.CouponProjection;
import com.mujian.backend.coupon.vo.CouponViews.*;
import com.mujian.backend.order.domain.CheckoutQuoteData;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 同一候选和分摊计算供报价与提交复核；不锁券、不静默替换用户选择。 */
@Service
public class CheckoutCoupons {
  public record Evaluation(
      List<Choice> choices, Integer couponVersion, Map<String, Integer> itemDiscounts) {}

  private final CouponMapper couponMapper;
  private final CouponReader couponReader;
  private final Clock clock;

  /** 复用用户券读取和纯整数分摊。 */
  public CheckoutCoupons(CouponMapper couponMapper, CouponReader couponReader, Clock clock) {
    this.couponMapper = couponMapper;
    this.couponReader = couponReader;
    this.clock = clock;
  }

  /** 按实际节省排序，所有范围判断使用模板白名单。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Evaluation evaluate(
      String actor, String selected, List<CheckoutQuoteData.Factory> factoryListFactories) {
    List<CouponProjection> couponProjectionList = couponMapper.selectCandidateCouponsByUser(actor);
    List<Coupon> couponListCoupons = couponReader.views(couponProjectionList);
    Map<String, CouponProjection> couponProjectionMap = new HashMap<>();
    for (CouponProjection couponProjection : couponProjectionList)
      couponProjectionMap.put(couponProjection.id(), couponProjection);
    List<Choice> choiceListChoices = new ArrayList<>();
    Map<String, Integer> discounts = new HashMap<>();
    Integer couponVersion = null;
    LocalDateTime at = now(clock);
    boolean found = selected == null;
    for (Coupon coupon : couponListCoupons) {
      CouponProjection couponProjection = couponProjectionMap.get(coupon.id());
      String reason = null;
      int saving = 0;
      List<CouponAllocation.Factory> factoryListWeights = new ArrayList<>();
      long eligibleRent = 0;
      for (CheckoutQuoteData.Factory factory : factoryListFactories) {
        boolean eligible =
            "all".equals(coupon.scopeType())
                || "factory".equals(coupon.scopeType())
                    && coupon.factoryIds().contains(factory.factoryId());
        if (eligible) eligibleRent += factory.amounts().rentOriginalFen();
        factoryListWeights.add(
            new CouponAllocation.Factory(
                factory.factoryId(),
                factory.amounts().rentOriginalFen(),
                Math.addExact(factory.amounts().depositFen(), factory.amounts().freightFen()),
                eligible));
      }
      if (!Set.of(TradingState.available.name(), TradingState.returned.name())
          .contains(couponProjection.couponStatus()))
        reason = TradingReason.COUPON_UNAVAILABLE.name();
      else if (at.isBefore(couponProjection.validFrom())) reason = "COUPON_NOT_STARTED";
      else if (!at.isBefore(couponProjection.validUntil())) reason = "COUPON_EXPIRED";
      else if (eligibleRent == 0) reason = "COUPON_SCOPE_MISMATCH";
      else if (eligibleRent < coupon.thresholdRentFen()) reason = "COUPON_THRESHOLD_NOT_MET";
      else {
        saving =
            CouponAllocation.saving(
                coupon.type(),
                coupon.faceAmountFen(),
                coupon.discountRateBps(),
                coupon.thresholdRentFen(),
                factoryListWeights);
        if (saving == 0) reason = "COUPON_NO_SAVING";
      }
      choiceListChoices.add(new Choice(coupon, reason == null, saving, reason));
      if (coupon.id().equals(selected)) {
        found = true;
        if (reason != null) throw conflict(TradingReason.COUPON_UNAVAILABLE.name());
        couponVersion = couponProjection.version();
        Map<String, Integer> factoryDiscounts = new HashMap<>();
        for (CouponAllocation.Share share : CouponAllocation.factories(saving, factoryListWeights))
          factoryDiscounts.put(share.id(), share.discountFen());
        for (CheckoutQuoteData.Factory factory : factoryListFactories) {
          List<CouponAllocation.Item> itemListItems = new ArrayList<>();
          for (CheckoutQuoteData.Item item : factory.items())
            itemListItems.add(
                new CouponAllocation.Item(
                    item.snapshotItemId(), factory.factoryId(), item.rentOriginalFen()));
          for (CouponAllocation.Share share :
              CouponAllocation.items(factoryDiscounts.get(factory.factoryId()), itemListItems))
            discounts.put(share.id(), share.discountFen());
        }
      }
    }
    if (!found) throw conflict(TradingReason.COUPON_UNAVAILABLE.name());
    choiceListChoices.sort(
        Comparator.comparing(Choice::usable)
            .reversed()
            .thenComparing(Comparator.comparingInt(Choice::savingFen).reversed())
            .thenComparing(choice -> choice.coupon().id()));
    return new Evaluation(List.copyOf(choiceListChoices), couponVersion, Map.copyOf(discounts));
  }
}
