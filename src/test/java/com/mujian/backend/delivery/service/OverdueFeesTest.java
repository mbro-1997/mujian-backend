package com.mujian.backend.delivery.service;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.delivery.mapper.OverdueFeeMapper.Line;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverdueFeesTest {
  /** 核对上海日期闭区间及项目价格快照。 */
  @Test
  void inclusiveShanghaiBoundariesAndItemPriceSnapshots() {
    long day = 86400000L;
    long[] offsets = {
      -1, 0, 1, day - 1, day, day + 1, 2 * day - 1, 2 * day, 2 * day + 1, 3 * day - 1, 3 * day,
      3 * day + 1
    };
    int[] days = {0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4};
    int[] caps = {0, 0, 0, 0, 0, 50, 50, 50, 150, 150, 150, 503};
    LocalDate due = LocalDate.of(2026, 11, 10);
    for (int i = 0; i < offsets.length; i++) {
      java.time.LocalDateTime end = due.plusDays(1).atStartOfDay().plusNanos(offsets[i] * 1000000L);
      long elapsed = OverdueFees.elapsed(due, end);
      assertEquals(days[i], OverdueFees.days(elapsed));
      assertEquals(caps[i], OverdueFees.cap(elapsed, 503));
      assertEquals(
          days[i] * 12000,
          OverdueFees.rent(List.of(new Line("A", 2, 5000), new Line("B", 1, 2000)), days[i]));
    }
  }
}
