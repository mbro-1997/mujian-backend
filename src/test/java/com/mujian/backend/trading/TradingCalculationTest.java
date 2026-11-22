package com.mujian.backend.trading;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.coupon.policy.CouponAllocation;
import com.mujian.backend.delivery.policy.TransportCalculation;
import com.mujian.backend.order.policy.CancellationCalculation;
import com.mujian.backend.order.policy.TradingTime;
import com.mujian.backend.payment.policy.RefundIdentity;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TradingCalculationTest {
  /** 检查价格取整及溢出。 */
  @Test
  void pricingRoundingAndOverflow() {
    for (int[] sample : new int[][] {{10000, 5000}, {10200, 5300}, {11000, 5300}, {11001, 5600}})
      assertEquals(
          sample[1],
          TransportCalculation.price(sample[0], 10000, 4000, 5000, 3000, 300).guideMaxFen());
    assertThrows(
        ArithmeticException.class,
        () -> TransportCalculation.price(Integer.MAX_VALUE, 0, 0, 1, 1, Integer.MAX_VALUE));
    assertEquals(
        5000, TransportCalculation.price(12000, 10000, 4000, 5000, 3000, null).guideMaxFen());
  }

  /** 检查包装旋转和重量边界。 */
  @Test
  void packagingRotationAndExactWeight() {
    TransportCalculation.Item item =
        new TransportCalculation.Item(
            new BigDecimal("2.00"),
            new BigDecimal("3.00"),
            new BigDecimal("4.00"),
            new BigDecimal("1.25"),
            12000,
            2);
    assertEquals("58", TransportCalculation.load(List.of(item)).volumeCm3().toString());
    assertEquals("2500", TransportCalculation.load(List.of(item)).weightGram().toString());
    TransportCalculation.Vehicle vehicle =
        new TransportCalculation.Vehicle("a", 40, 20, 30, 60, 2500, 1);
    assertEquals(vehicle, TransportCalculation.select(List.of(item), List.of(vehicle)));
    assertNull(
        TransportCalculation.select(
            List.of(item),
            List.of(new TransportCalculation.Vehicle("a", 40, 19, 30, 60, 2500, 1))));
  }

  /** 核对封顶优惠及尾差分摊后金额守恒。 */
  @Test
  void cappedCouponAndStableTailConserveMoney() {
    List<CouponAllocation.Factory> factories =
        List.of(
            new CouponAllocation.Factory("a", 1, 0, true),
            new CouponAllocation.Factory("b", 100, 0, true),
            new CouponAllocation.Factory("c", 100, 0, true),
            new CouponAllocation.Factory("d", 100, 0, false));
    int saving = CouponAllocation.saving("fixed_amount", 1000, null, 0, factories);
    assertEquals(198, saving);
    assertEquals(
        List.of(
            new CouponAllocation.Share("a", 0),
            new CouponAllocation.Share("b", 99),
            new CouponAllocation.Share("c", 99),
            new CouponAllocation.Share("d", 0)),
        CouponAllocation.factories(saving, factories));
    Random random = new Random(71);
    for (int run = 0; run < 500; run++) {
      List<CouponAllocation.Factory> input = new ArrayList<>();
      for (int i = 0; i < 5; i++)
        input.add(
            new CouponAllocation.Factory(
                "f" + i, 1 + random.nextInt(10000), random.nextInt(2), random.nextBoolean()));
      int discount =
          CouponAllocation.saving("percentage", null, random.nextInt(10000) + 1, 0, input);
      List<CouponAllocation.Share> output = CouponAllocation.factories(discount, input);
      long sum = 0;
      for (int i = 0; i < 5; i++) {
        int share = output.get(i).discountFen();
        sum += share;
        assertTrue(share >= 0 && share <= input.get(i).rentFen());
        assertTrue(input.get(i).rentFen() + input.get(i).otherCashFen() - share >= 1);
        if (!input.get(i).eligible()) assertEquals(0, share);
      }
      assertEquals(discount, sum);
      Collections.reverse(input);
      Map<String, Integer> prior = new HashMap<>();
      for (CouponAllocation.Share share : output) prior.put(share.id(), share.discountFen());
      for (CouponAllocation.Share share : CouponAllocation.factories(discount, input))
        assertEquals(prior.get(share.id()), share.discountFen());
    }
  }

  /** 固定时钟核对取消费用的时间边界。 */
  @Test
  void timeBoundariesAndCancellationUseControlledTime() {
    LocalDate start = LocalDate.of(2026, 11, 9);
    assertFalse(TradingTime.canSubmit(start, start.minusDays(14).atStartOfDay().minusNanos(1)));
    assertTrue(TradingTime.canSubmit(start, start.minusDays(14).atStartOfDay()));
    LocalDateTime submitted = start.minusDays(2).atTime(10, 59);
    assertTrue(TradingTime.canSubmit(start, submitted));
    assertFalse(TradingTime.canSubmit(start, submitted.plusMinutes(1)));
    assertEquals(submitted.plusMinutes(10), TradingTime.paymentExpiry(submitted));
    assertTrue(TradingTime.canCreatePayment(submitted.plusMinutes(10), submitted.plusMinutes(9)));
    assertFalse(
        TradingTime.canCreatePayment(
            submitted.plusMinutes(10), submitted.plusMinutes(9).plusSeconds(1)));
    assertFalse(
        TradingTime.canCreatePayment(
            submitted.plusMinutes(10), submitted.plusMinutes(9).plusNanos(1)));
    assertEquals(
        0,
        CancellationCalculation.calculate(
                true, start, start.atStartOfDay().minusHours(48).minusNanos(1), 100, 100, 20, 30)
            .deductionFen());
    assertEquals(
        30,
        CancellationCalculation.calculate(
                true, start, start.atStartOfDay().minusHours(48), 100, 100, 20, 30)
            .deductionFen());
    assertEquals(
        30,
        CancellationCalculation.calculate(
                true, start, start.atStartOfDay().minusHours(24), 100, 100, 20, 30)
            .deductionFen());
    assertEquals(
        25,
        CancellationCalculation.calculate(
                true, start, start.atStartOfDay().minusHours(24).plusNanos(1), 100, 25, 20, 30)
            .deductionFen());
    assertFalse(TradingTime.canAccept(submitted, submitted));
  }

  /** 区分五类退款义务的稳定键。 */
  @Test
  void fiveRefundObligationsHaveDistinctStableKeys() {
    String order = "00000000000040008000000000000001", sub = "00000000000040008000000000000002";
    Set<String> keys = new HashSet<>();
    for (String reason :
        List.of(
            "user_cancel_before_accept",
            "user_cancel_rule",
            "factory_manual_reject",
            "factory_timeout_reject")) {
      String key = RefundIdentity.key("rental_order", order, sub, reason);
      assertEquals(key, RefundIdentity.key("rental_order", order, sub, reason));
      keys.add(key);
    }
    keys.add(RefundIdentity.key("checkout_batch", order, sub, "unformed_batch_refund"));
    assertEquals(5, keys.size());
    assertThrows(
        IllegalArgumentException.class,
        () -> RefundIdentity.key("checkout_batch", order, sub, "user_cancel_rule"));
  }
}
