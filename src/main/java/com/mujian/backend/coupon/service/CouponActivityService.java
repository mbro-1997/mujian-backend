package com.mujian.backend.coupon.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.coupon.mapper.DistributionMapper;
import com.mujian.backend.coupon.projection.DistributionRows.*;
import com.mujian.backend.coupon.vo.ActivityViews.*;
import com.mujian.backend.factory.service.FactoryAccess;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CouponActivityService {
  private final DistributionMapper distributionMapper;
  private final FactoryAccess factoryAccess;
  private final Clock clock;

  /** 发现与领取共用当前资格和业务时钟。 */
  public CouponActivityService(
      DistributionMapper distributionMapper, FactoryAccess factoryAccess, Clock clock) {
    this.distributionMapper = distributionMapper;
    this.factoryAccess = factoryAccess;
    this.clock = clock;
  }

  /** 只允许启用的租赁方查看和领取活动。 */
  private void authorize(String user) {
    if (!D2Codes.CODE_DEMANDER.equals(factoryAccess.context(user, true).roleCode()))
      throw ApiFailure.denied(D2Codes.CODE_PERMISSION_DENIED);
  }

  /** 读操作不会预留名额或生成持券。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<Offer> offers(String user, PageQuery page) {
    authorize(user);
    LocalDateTime at = now(clock);
    boolean student = distributionMapper.hasStudentBenefit(user, at);
    long count = distributionMapper.countOffers(user, student, at);
    if (count <= page.offset()) return PageResult.of(List.of(), count, page);
    List<OfferRow> rows = distributionMapper.selectOffers(user, student, at, page);
    Set<String> scoped = new HashSet<>();
    for (OfferRow offerRow : rows)
      if ("factory".equals(offerRow.applicableScopeType())) scoped.add(offerRow.templateId());
    Map<String, List<Factory>> scopes = new HashMap<>();
    if (!scoped.isEmpty())
      for (Scope scope : distributionMapper.selectScopes(scoped)) {
        List<Factory> factories = scopes.get(scope.templateId());
        if (factories == null) {
          factories = new ArrayList<>();
          scopes.put(scope.templateId(), factories);
        }
        factories.add(new Factory(scope.factoryId(), scope.factoryName()));
      }
    List<Offer> offers = new ArrayList<>();
    for (OfferRow offerRowRow : rows)
      offers.add(
          new Offer(
              offerRowRow.activityId(),
              offerRowRow.templateId(),
              offerRowRow.templateName(),
              offerRowRow.couponType(),
              offerRowRow.faceAmountFen(),
              offerRowRow.discountRateBps(),
              offerRowRow.thresholdRentAmountFen(),
              offerRowRow.applicableScopeType(),
              scopes.getOrDefault(offerRowRow.templateId(), List.of()),
              offerRowRow.conditionCode(),
              time(offerRowRow.endsAt()),
              offerRowRow.validityType(),
              offerRowRow.validDays(),
              time(offerRowRow.validStartAt()),
              time(offerRowRow.validEndAt())));
    return PageResult.of(offers, count, page);
  }

  /** 同一活动同一用户只生成一张，过窗后仍可恢复成功结果。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Claim claim(String user, String activityId) {
    factoryAccess.lock(user);
    authorize(user);
    String key = "claim:" + id(activityId) + ":user:" + user;
    Claim previous = distributionMapper.selectClaim(key, user, now(clock));
    if (previous != null) return previous;
    Activity activity = distributionMapper.lockActivity(activityId);
    if (activity == null) throw missing();
    Template template = distributionMapper.lockTemplate(activity.templateId());
    LocalDateTime at = now(clock);
    previous = distributionMapper.selectClaim(key, user, at);
    if (previous != null) return previous;
    if (!"active".equals(activity.status())
        || at.isBefore(activity.startsAt())
        || !at.isBefore(activity.endsAt())) throw conflict(BusinessFailureReason.ACTIVITY_NOT_OPEN.name());
    if (!template.issuable(at)) throw conflict(BusinessFailureReason.TEMPLATE_NOT_ISSUABLE.name());
    if (!"all_active".equals(activity.conditionCode())
        && !("student_verified".equals(activity.conditionCode())
            && distributionMapper.hasStudentBenefit(user, at)))
      throw conflict(BusinessFailureReason.CLAIM_NOT_ELIGIBLE.name());
    CouponWrite coupon = new CouponWrite(Ids.next(), user, key, Ids.next());
    distributionMapper.insertCoupons(
        List.of(coupon), template, "conditional_claim", activityId, at);
    distributionMapper.insertIssuedLogs(
        List.of(coupon), "user", user, "distribution", activityId, "用户领取活动券", at);
    distributionMapper.incrementActivity(activityId);
    distributionMapper.incrementTemplate(template.id(), 1, at);
    return new Claim(coupon.id(), "available", false);
  }
}
