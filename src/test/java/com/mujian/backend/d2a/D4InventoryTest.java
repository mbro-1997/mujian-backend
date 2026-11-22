package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.prop.mapper.PropSkuMapper;
import com.mujian.backend.prop.mapper.PropSkuStockMapper;
import com.mujian.backend.prop.mapper.PropSnInstanceMapper;
import com.mujian.backend.prop.policy.PropAvailability;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 真实隔离SQL验证逾期扫描前后的同一库存规则，沿用D3身份/库存夹具。 */
class D4InventoryTest {
  /** 逾期任务尚未执行、持久状态已转换及完成释放三种事实分别核对。 */
  @Test
  void overdueOccupancyProtectsBrowseAllocationDashboardAndReductionBeforeJob() throws Exception {
    D3BQuoteTest fixture = new D3BQuoteTest();
    try {
      fixture.start();
      D2ApplicationTest application = fixture.f;
      LocalDate today = now(application.clock).toLocalDate();
      String reservation =
          fixture.reservation(
              fixture.sku,
              null,
              today.minusDays(3),
              today.minusDays(1),
              4,
              "renting",
              now(application.clock));
      PropSkuStockMapper propSkuStockMapper = application.app.getBean(PropSkuStockMapper.class);
      PropSkuMapper propSkuMapper = application.app.getBean(PropSkuMapper.class);
      for (String status : List.of("renting", "overdue_holding")) {
        application.db.update(
            "UPDATE inventory_reservation SET reservation_status=? WHERE id=?",
            status,
            reservation);
        assertEquals(
            2,
            propSkuStockMapper
                .selectStockOccupancyListBySkuIdsAndDates(
                    List.of(fixture.sku), today.plusDays(10), LocalDate.of(9999, 12, 31), today)
                .get(0)
                .getAmount()
                .intValueExact());
        assertEquals(
            2,
            propSkuStockMapper
                .selectCurrentStockQuantities(fixture.sku, today)
                .available()
                .intValueExact());
        assertEquals(
            4,
            propSkuStockMapper
                .selectCurrentStockQuantities(fixture.sku, today)
                .renting()
                .intValueExact());
        assertEquals(
            2,
            propSkuMapper
                .selectPropInventorySummaryListBySkuIds(List.of(fixture.sku), today)
                .get(0)
                .getAvailableQuantity()
                .intValue());
      }
      application.db.update(
          "UPDATE inventory_reservation SET reservation_status='completed' WHERE id=?",
          reservation);
      assertEquals(
          6,
          propSkuStockMapper
              .selectCurrentStockQuantities(fixture.sku, today)
              .available()
              .intValueExact());
      JsonNode made = fixture.fixture.create(fixture.supplier, "A_ASSET", 1);
      String sku = made.path("skuId").asString();
      fixture.fixture.approve("sku_audit", made.path("targetId").asString(), 0);
      String sn =
          application.db.queryForObject(
              "SELECT id FROM prop_sn_instance WHERE sku_id=?", String.class, sku);
      application.db.update("UPDATE prop_sn_instance SET instance_status='idle' WHERE id=?", sn);
      String assetReservation =
          fixture.reservation(
              sku,
              sn,
              today.minusDays(3),
              today.minusDays(1),
              1,
              "renting",
              now(application.clock));
      PropSnInstanceMapper propSnInstanceMapper =
          application.app.getBean(PropSnInstanceMapper.class);
      assertEquals(
          0,
          propSnInstanceMapper
              .selectRentableSnQuantityListBySkuIdsAndDates(
                  List.of(sku), today.plusDays(10), today.plusDays(11), today)
              .size());
      assertTrue(
          propSnInstanceMapper
              .selectAvailableSnIdsForUpdate(sku, today.plusDays(10), today.plusDays(11), 1, today)
              .isEmpty());
      application.db.update(
          "UPDATE inventory_reservation SET reservation_status='completed' WHERE id=?",
          assetReservation);
      PropAvailability propAvailability = application.app.getBean(PropAvailability.class);
      assertEquals(
          1,
          propAvailability
              .quantities(
                  List.of(sku), new PropAvailability.Dates(today.plusDays(10), today.plusDays(11)))
              .get(sku));
    } finally {
      fixture.stop();
    }
  }
}
