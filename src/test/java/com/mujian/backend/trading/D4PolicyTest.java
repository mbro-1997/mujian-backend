package com.mujian.backend.trading;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.TransportServiceMode;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.delivery.policy.FulfillmentTime;
import com.mujian.backend.delivery.service.DisputeGuard;
import com.mujian.backend.payment.policy.RefundIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** 不实际等待12/72小时，验证正式时间公式、未知门禁和退款稳定来源。 */
class D4PolicyTest {
  /** 不同去程共享确认后的规则，早到与重启不能延后原窗口。 */
  @Test
  void windowsUseOriginalFactsAcrossCalendarDays() {
    LocalDateTime sent = LocalDateTime.of(2026, 11, 9, 23, 30);
    for (TransportServiceMode mode :
        new TransportServiceMode[] {
          TransportServiceMode.factory_delivery, TransportServiceMode.factory_assisted_call
        }) {
      assertEquals(sent.plusHours(6), FulfillmentTime.receipt(mode, sent).startAt());
      assertEquals(sent.plusHours(18), FulfillmentTime.receipt(mode, sent).deadlineAt());
    }
    assertEquals(
        sent.plusHours(12),
        FulfillmentTime.receipt(TransportServiceMode.self_pickup, sent).deadlineAt());
    FulfillmentTime.ReturnWindow window = FulfillmentTime.userReturn(sent.minusDays(1), sent, sent);
    assertEquals(sent.plusHours(12), window.startAt());
    assertEquals(sent.plusHours(24), window.warningAt());
    assertEquals(sent.plusHours(84), window.autoCloseAt());
    LocalDateTime early = sent.plusHours(2);
    assertEquals(
        early.plusHours(72), FulfillmentTime.arrival(window.startAt(), early).autoCloseAt());
    assertEquals(early, FulfillmentTime.arrival(early, sent.plusDays(5)).startAt());
    assertEquals(early, FulfillmentTime.pickup(sent, early).startAt());
    assertThrows(ApiFailure.class, () -> FulfillmentTime.pickup(sent, sent.minusNanos(1)));
    assertThrows(ApiFailure.class, () -> FulfillmentTime.userReturn(sent.plusHours(1), sent, sent));
    assertThrows(
        ApiFailure.class,
        () -> FulfillmentTime.userReturn(sent.minusHours(1), sent.plusSeconds(1), sent));
  }

  /** 单独打开配置不能把未接通D5伪装成没有争议。 */
  @Test
  void protectionRequiresEnabledAndKnownFactsAndChecksEveryConflict() {
    assertEquals(
        "FULFILLMENT_NOT_OPEN",
        assertThrows(
                ApiFailure.class,
                () ->
                    new FulfillmentProtection(id -> new DisputeGuard.Facts(false,false,false,false), true)
                        .requireNormal("order"))
            .reason);
    DisputeGuard clear = id -> new DisputeGuard.Facts(true, false, false, false);
    assertEquals(
        "FULFILLMENT_NOT_OPEN",
        assertThrows(
                ApiFailure.class,
                () -> new FulfillmentProtection(clear, false).requireNormal("order"))
            .reason);
    assertDoesNotThrow(() -> new FulfillmentProtection(clear, true).requireNormal("order"));
    assertEquals(
        "RETURN_CLAIM_EXISTS",
        assertThrows(
                ApiFailure.class,
                () ->
                    new FulfillmentProtection(
                            id -> new DisputeGuard.Facts(true, false, true, false), true)
                        .requireNormal("order"))
            .reason);
    for (DisputeGuard.Facts facts :
        new DisputeGuard.Facts[] {
          new DisputeGuard.Facts(true, true, false, false),
          new DisputeGuard.Facts(true, false, false, true)
        }) {
      assertEquals(
          "FULFILLMENT_PAUSED",
          assertThrows(
                  ApiFailure.class,
                  () -> new FulfillmentProtection(id -> facts, true).requireNormal("order"))
              .reason);
    }
  }

  /** D3原键字节不变；D4必须使用真实核验/申报来源且不可交换科目。 */
  @Test
  void refundKeysKeepV1AndSeparateNormalSourceFamilies() throws Exception {
    String source = "10000000000040008000000000000001";
    String suborder = "20000000000040008000000000000001";
    for (String[] tuple :
        new String[][] {
          {"v1", "rental_order", "user_cancel_rule"},
          {"v2", "return_check", "deposit_release"},
          {"v2", "expense_statement", "freight_pool_release"}
        }) {
      String canonical =
          "[\"" + tuple[0] + "\",\"" + tuple[1] + "\",\"" + source + "\",\"" + suborder + "\",\""
              + tuple[2] + "\"]";
      assertEquals(
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(canonical.getBytes(StandardCharsets.UTF_8))),
          RefundIdentity.key(tuple[1], source, suborder, tuple[2]));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> RefundIdentity.key("rental_order", source, suborder, "deposit_release"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RefundIdentity.key("return_check", source, suborder, "freight_pool_release"));
  }
}
