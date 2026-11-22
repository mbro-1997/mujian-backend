package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.delivery.service.DisputeGuard;
import com.mujian.backend.delivery.service.OverdueOrders;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class D5ExtensionQuoteHttpTest {
  /** 原有真实订单上验证报价日界、身份、租期上限与批准后的逾期消费口径。 */
  @Test
  void quoteWindowAndEffectiveDueDate() throws Exception {
    D4RecoveryHttpTest setup=new D4RecoveryHttpTest();
    D3BQuoteTest fixture=setup.fixture();
    fixture.extraSources=new Class<?>[0];
    try {
      fixture.start();
      String orderId=setup.dispatch(fixture);
      String base="/api/me/rental-orders/"+orderId;
      fixture.call("POST",base+"/receipt/confirm",view("expectedVersion",0),fixture.buyer,fixture.key(),200);
      String path=base+"/extension-quote?extraDays=";
      JsonNode quote=fixture.call("GET",path+"1",null,fixture.buyer,null,200);
      int max=quote.path("maxExtraDays").asInt();
      assertTrue(max>0 && max<120);
      int rent=fixture.f.db.queryForObject("SELECT SUM(quantity*daily_rent_price_fen) FROM rental_order_item WHERE rental_order_id=?",Integer.class,orderId);
      assertEquals(rent,quote.path("rentFen").asInt());
      Instant deadline=OffsetDateTime.parse(quote.path("latestPaidAt").asString()).toInstant();
      fixture.f.clock.at=deadline.minusMillis(1);
      fixture.call("GET",path+max,null,fixture.buyer,null,200);
      fixture.f.clock.at=deadline;
      fixture.call("GET",path+max,null,fixture.buyer,null,200);
      fixture.call("GET",path+(max+1),null,fixture.buyer,null,409);
      assertEquals("EXTENSION_DAYS_EXCEEDED",fixture.f.lastReason);
      fixture.call("GET",path+"1",null,fixture.f.user(),null,404);
      fixture.call("GET",path+"1",null,fixture.supplier,null,403);
      fixture.f.clock.at=deadline.plusMillis(1);
      fixture.call("GET",path+"1",null,fixture.buyer,null,409);
      assertEquals("EXTENSION_WINDOW_CLOSED",fixture.f.lastReason);

      fixture.f.db.update("UPDATE rental_order SET current_due_date=DATE_ADD(rental_end_date,INTERVAL 2 DAY) WHERE id=?",orderId);
      fixture.f.clock.at=deadline.plusSeconds(48*3600);
      OverdueOrders overdueOrders=fixture.f.app.getBean(OverdueOrders.class);
      overdueOrders.transition(orderId);
      assertEquals("renting",fixture.call("GET",base,null,fixture.buyer,null,200).path("fulfillmentStage").asString());
      assertFalse(fixture.call("GET",base+"/fulfillment",null,fixture.buyer,null,200).path("overdue").asBoolean());
      fixture.f.clock.at=fixture.f.clock.at.plusSeconds(2*86400);
      overdueOrders.transition(orderId);
      assertEquals("overdue",fixture.call("GET",base,null,fixture.buyer,null,200).path("fulfillmentStage").asString());
      assertEquals(1,fixture.f.db.queryForObject("SELECT COUNT(*) FROM order_fulfillment_event e JOIN rental_order o ON o.id=e.rental_order_id WHERE o.id=? AND e.event_type='rental_overdue' AND e.event_time=DATE_ADD(o.current_due_date,INTERVAL 1 DAY)",Integer.class,orderId));

      DisputeGuard disputeGuard=fixture.f.app.getBean(DisputeGuard.class);
      assertFalse(disputeGuard.inspect(com.mujian.backend.common.support.Ids.next()).ready());
      assertTrue(disputeGuard.inspect(orderId).ready());
    } finally {
      setup.cleanup(fixture);
    }
  }
}
