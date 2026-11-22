package com.mujian.backend.prop.policy;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.PropAssetType;
import com.mujian.backend.common.enums.PropAvailabilityReason;
import com.mujian.backend.common.enums.PropRentableStatus;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.mapper.PropSkuStockMapper;
import com.mujian.backend.prop.mapper.PropSnInstanceMapper;
import com.mujian.backend.prop.projection.PropQuantityProjection;
import com.mujian.backend.prop.vo.AvailabilityVO;
import java.time.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.HashMap;
import org.springframework.stereotype.Service;

@Service
public class PropAvailability {
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final PropSkuStockMapper propSkuStockMapper;
  private final Clock clock;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropAvailability(
      PropSnInstanceMapper propSnInstanceMapper,
      PropSkuStockMapper propSkuStockMapper,
      Clock clock) {
    this.propSnInstanceMapper = propSnInstanceMapper;
    this.propSkuStockMapper = propSkuStockMapper;
    this.clock = clock;
  }

  public record Dates(LocalDate start, LocalDate end) {}

  /** 校验租期边界并转换为统一日期区间。 */
  public Dates dates(String start, String end, boolean history) {
    if (start == null && end == null) return null;
    if (start == null || end == null) throw ApiFailure.invalid();
    try {
      LocalDate a = LocalDate.parse(start);
      LocalDate b = LocalDate.parse(end);
      if (b.isBefore(a)
          || !history
              && (a.isBefore(LocalDateTime.ofInstant(clock.instant(), ZONE).toLocalDate())
                  || ChronoUnit.DAYS.between(a, b) >= 120)) throw ApiFailure.invalid();
      return new Dates(a, b);
    } catch (java.time.DateTimeException dateTimeException) {
      throw ApiFailure.invalid();
    }
  }

  /**
   * All candidate IDs enter the calculation. Paging and rentable-first sorting happen afterwards.
   */
  /** 按资产类型计算租期内可租量，B类使用占用峰值。 */
  public Map<String, Integer> quantities(Collection<String> ids, Dates dates) {
    HashMap<String, Integer> result = new HashMap<String, Integer>();
    if (ids.isEmpty()) return result;

    List<String> skuIds = new ArrayList<>(ids);
    // 每件SKU的占用计算彼此独立；分批只限制SQL参数，不提前截断候选集合。
    for (int start = 0; start < skuIds.size(); start += 500) {
      List<String> batchIds = skuIds.subList(start, Math.min(start + 500, skuIds.size()));
      for (PropQuantityProjection propQuantityProjection :
          propSnInstanceMapper.selectRentableSnQuantityListBySkuIdsAndDates(
              batchIds, dates.start(), dates.end().plusDays(1), now(clock).toLocalDate()))
        result.put(
            propQuantityProjection.getSkuId(), propQuantityProjection.getAmount().intValueExact());

      for (PropQuantityProjection propQuantityProjectionStock :
          propSkuStockMapper.selectStockOccupancyListBySkuIdsAndDates(
              batchIds, dates.start(), dates.end().plusDays(1), now(clock).toLocalDate()))
        result.put(
            propQuantityProjectionStock.getSkuId(),
            propQuantityProjectionStock.getAmount().intValueExact());
    }
    return result;
  }

  /** 按既定字段构造业务结果，保持状态和时间表达。 */
  public AvailabilityVO result(PropSkuDO propSkuDO, Dates dates, int count, boolean eligible) {
    if (!eligible) count = 0;
    return new AvailabilityVO()
        .startDate(dates.start().toString())
        .endDate(dates.end().toString())
        .maxRentableQuantity(count)
        .rentableStatus(
            (count > 0 ? PropRentableStatus.RENTABLE : PropRentableStatus.UNAVAILABLE).code())
        .reasonCode(
            count > 0
                ? null
                : !eligible
                    ? PropAvailabilityReason.SKU_NOT_RENTABLE.name()
                    : propSkuDO.getAssetType().equals(PropAssetType.A_ASSET.name())
                        ? PropAvailabilityReason.SN_NOT_AVAILABLE.name()
                        : PropAvailabilityReason.STOCK_NOT_ENOUGH.name());
  }
}
