package com.mujian.backend.order.domain;

import com.mujian.backend.address.domain.ShippingAddressDO;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.infrastructure.map.RouteGateway;
import com.mujian.backend.order.dto.CheckoutDtos.QuoteInput;
import com.mujian.backend.order.vo.CheckoutViews.Amounts;
import com.mujian.backend.planning.domain.SnapshotV1;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 报价凭据白名单；稳定图key与明确版本，禁止临时URL和支付参数进入凭据。 */
public record CheckoutQuoteData(
    int schemaVersion,
    String buyerId,
    LocalDateTime issuedAt,
    LocalDateTime expiresAt,
    QuoteInput input,
    LocalDate startDate,
    LocalDate endDate,
    int rentalDays,
    ShippingAddressDO address,
    Integer couponVersion,
    List<Factory> factories,
    Amounts amounts) {
  public record Item(
      String snapshotItemId,
      String sourceItemId,
      int skuVersion,
      SnapshotV1 snapshot,
      Integer packagingFactorBps,
      int quantity,
      int rentOriginalFen,
      int couponDiscountFen,
      int rentCashFen,
      int depositFen,
      String transportRequirement) {}

  public record Leg(
      String serviceMode,
      String chargeMode,
      RouteGateway.Route route,
      VehicleDO vehicle,
      PriceRuleDO rule,
      String routeStrategy,
      String volumeCm3,
      String weightGram,
      Integer extraKmCount,
      int guideMinFen,
      int guideMaxFen,
      int prepaidFen) {}

  public record Factory(
      String factoryId,
      String ownerUserId,
      String factoryName,
      String address,
      long profileVersion,
      String merchantId,
      String subMchid,
      int merchantVersion,
      List<Item> items,
      Leg outbound,
      Leg returnLeg,
      String buyerRemark,
      Amounts amounts) {}
}
