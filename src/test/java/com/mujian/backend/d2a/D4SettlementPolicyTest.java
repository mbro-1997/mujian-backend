package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.settlement.policy.SettlementAmounts;
import com.mujian.backend.settlement.policy.SettlementAmounts.*;
import com.mujian.backend.settlement.service.SettlementGeneration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class D4SettlementPolicyTest {
  /** 核对订单分摊按余数及稳定来源收尾。 */
  @Test
  void allocateOrderRoundingByRemainderThenStableSource() {
    assertEquals(
        List.of(new Split("a", 1, 2), new Split("b", 0, 5)),
        SettlementAmounts.split(List.of(new Cash("b", 3, 2), new Cash("a", 3, 0))));
    assertEquals(
        List.of(new Split("a", 0, 1), new Split("b", 1, 3)),
        SettlementAmounts.split(List.of(new Cash("b", 4, 0), new Cash("a", 1, 0))));
    assertEquals(
        List.of(new Split("z", 0, 7)), SettlementAmounts.split(List.of(new Cash("z", 0, 7))));
    assertThrows(
        ArithmeticException.class,
        () ->
            SettlementAmounts.split(
                List.of(new Cash("a", Integer.MAX_VALUE, 0), new Cash("b", 1, 0))));
  }

  /** 固定上海时间验证截点切换。 */
  @Test
  void cutoffChangesAtShanghaiTwo() {
    LocalDateTime date = LocalDateTime.parse("2026-11-09T02:00:00");
    assertEquals(date.toLocalDate().atStartOfDay(), SettlementGeneration.cutoff(date));
    assertEquals(
        date.toLocalDate().minusDays(1).atStartOfDay(),
        SettlementGeneration.cutoff(date.minusNanos(1)));
  }
}
