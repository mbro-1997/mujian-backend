package com.mujian.backend.prop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.prop.mapper.PropSkuStockMapper;
import com.mujian.backend.prop.mapper.PropSnInstanceMapper;
import com.mujian.backend.prop.policy.PropAvailability;
import com.mujian.backend.prop.policy.PropStyleFilter;
import com.mujian.backend.prop.projection.PropQuantityProjection;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PropReadBatchTest {
  /** 跨过500个参数后仍计算完整候选，A/B结果合并且空输入不查库。 */
  @Test
  void candidateChunkBoundaryKeepsEverySkuAndEmptyInputDoesNotQuery() {
    PropSnInstanceMapper propSnInstanceMapper = mock(PropSnInstanceMapper.class);
    PropSkuStockMapper propSkuStockMapper = mock(PropSkuStockMapper.class);
    List<Integer> batchSizes = new ArrayList<>();
    when(propSnInstanceMapper.selectRentableSnQuantityListBySkuIdsAndDates(
            anyCollection(), any(), any(), any()))
        .thenAnswer(invocation -> quantities(invocation.getArgument(0), 0, batchSizes));
    when(propSkuStockMapper.selectStockOccupancyListBySkuIdsAndDates(
            anyCollection(), any(), any(), any()))
        .thenAnswer(invocation -> quantities(invocation.getArgument(0), 1, new ArrayList<>()));
    PropAvailability propAvailability =
        new PropAvailability(propSnInstanceMapper, propSkuStockMapper, Clock.systemUTC());
    PropAvailability.Dates dates =
        new PropAvailability.Dates(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 3));
    assertTrue(propAvailability.quantities(List.of(), dates).isEmpty());
    verifyNoInteractions(propSnInstanceMapper, propSkuStockMapper);
    List<String> ids = new ArrayList<>();
    for (int index = 0; index < 501; index++) ids.add(Integer.toString(index));
    Map<String, Integer> result = propAvailability.quantities(ids, dates);
    assertEquals(List.of(500, 1), batchSizes);
    assertEquals(501, result.size());
    for (String id : ids) assertEquals(Integer.valueOf(Integer.parseInt(id) + 1), result.get(id));
    verify(propSnInstanceMapper, times(2))
        .selectRentableSnQuantityListBySkuIdsAndDates(
            anyCollection(), eq(dates.start()), eq(dates.end().plusDays(1)), any());
    verify(propSkuStockMapper, times(2))
        .selectStockOccupancyListBySkuIdsAndDates(
            anyCollection(), eq(dates.start()), eq(dates.end().plusDays(1)), any());
  }

  /** 模拟两类资产独立查询，返回每个候选可核对的数量。 */
  private List<PropQuantityProjection> quantities(
      Collection<String> ids, int parity, List<Integer> batchSizes) {
    batchSizes.add(ids.size());
    List<PropQuantityProjection> results = new ArrayList<>();
    for (String id : ids) {
      int index = Integer.parseInt(id);
      if (index % 2 != parity) continue;
      PropQuantityProjection propQuantityProjection = new PropQuantityProjection();
      propQuantityProjection.setSkuId(id);
      propQuantityProjection.setAmount(BigDecimal.valueOf(index + 1));
      results.add(propQuantityProjection);
    }
    return results;
  }

  /** 同一格式规则覆盖公开与管理筛选，未知编码不转为字典存在性校验。 */
  @Test
  void styleFilterKeepsUnknownCodesButRejectsAlternativeArrayEncodings() {
    assertNull(PropStyleFilter.validate(null));
    assertEquals(List.of("unknown"), PropStyleFilter.validate(List.of("unknown")));
    assertEquals(
        List.of("special_asset", "retro"),
        PropStyleFilter.validate(List.of("special_asset", "retro")));
    for (List<String> invalid :
        List.of(
            List.<String>of(),
            List.of("retro,modern"),
            List.of("[\"retro\"]"),
            List.of("retro", "retro"),
            List.of("a", "b", "c", "d"))) {
      assertThrows(ApiFailure.class, () -> PropStyleFilter.validate(invalid));
    }
  }
}
