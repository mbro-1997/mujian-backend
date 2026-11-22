package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.settlement.service.SettlementGeneration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 原D3合法接单后取消保留的现金租金，结算不能再扣一次取消费或优惠。 */
class D4CancellationSettlementTest {
  /** 核对取消费现金收入及争议冻结门禁。 */
  @Test
  void cancellationFeeRemainsCashIncomeAndBlockedDisputeCannotFreeze() throws Exception {
    D3BQuoteTest d3BQuoteTest = new D4RecoveryHttpTest().fixture();
    try {
      d3BQuoteTest.start();
      d3BQuoteTest.acceptedCancellationRequotesAtBoundaryAndKeepsDeductionOnRefund();
      D4OutboundHttpTest.KnownFacts guard = d3BQuoteTest.f.app.getBean(D4OutboundHttpTest.KnownFacts.class);
      SettlementGeneration generator = d3BQuoteTest.f.app.getBean(SettlementGeneration.class);
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plus(Duration.ofDays(2));
      guard.ready = false;
      generator.generate(d3BQuoteTest.factoryId);
      assertEquals(
          0,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM factory_settlement_batch WHERE factory_id=?",
              Integer.class,
              d3BQuoteTest.factoryId));
      guard.ready = true;
      generator.generate(d3BQuoteTest.factoryId);
      generator.generate(d3BQuoteTest.factoryId);
      assertEquals(
          1,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM factory_settlement_detail WHERE factory_id=? AND"
                  + " rent_settle_base_amount_fen=rent_deducted_amount_fen AND"
                  + " rent_deducted_amount_fen>0 AND shared_coupon_discount_amount_fen>0 AND"
                  + " platform_commission_amount_fen=FLOOR(rent_deducted_amount_fen/5)",
              Integer.class,
              d3BQuoteTest.factoryId));
    } finally {
      if (d3BQuoteTest.f.db != null && d3BQuoteTest.factoryId != null) {
        d3BQuoteTest.f.db.update(
            "DELETE w FROM wechat_profit_sharing_order w JOIN factory_settlement_batch b ON"
                + " b.id=w.settlement_batch_id WHERE b.factory_id=?",
            d3BQuoteTest.factoryId);
        for (String t :
            List.of(
                "factory_settlement_payment",
                "platform_commission_record",
                "factory_settlement_detail",
                "factory_settlement_batch"))
          d3BQuoteTest.f.db.update("DELETE FROM " + t + " WHERE factory_id=?", d3BQuoteTest.factoryId);
      }
      d3BQuoteTest.stop();
    }
  }
}
