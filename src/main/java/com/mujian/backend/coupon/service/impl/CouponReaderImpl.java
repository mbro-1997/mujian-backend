package com.mujian.backend.coupon.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.coupon.mapper.CouponMapper;
import com.mujian.backend.coupon.projection.*;
import com.mujian.backend.coupon.service.*;
import com.mujian.backend.coupon.vo.CouponViews.*;
import com.mujian.backend.factory.service.FactoryAccess;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 本人券读取和批量范围转换，不把道具缓存期限用于资金事实。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CouponReaderImpl implements CouponReader {
  private final CouponMapper couponMapper;
  private final FactoryAccess factoryAccess;
  private final Clock clock;

  /** 复用身份与统一时钟。 */
  public CouponReaderImpl(CouponMapper couponMapper, FactoryAccess factoryAccess, Clock clock) {
    this.couponMapper = couponMapper;
    this.factoryAccess = factoryAccess;
    this.clock = clock;
  }

  /** 合法历史查询不要求账号仍具备新增交易资格。 */
  public void authorize(String actor) {
    if (!D2Codes.CODE_DEMANDER.equals(factoryAccess.context(actor, false).roleCode()))
      throw ApiFailure.denied(TradingReason.DEMANDER_ROLE_REQUIRED.name());
  }

  /** 统计不受当前分页截断影响。 */
  public CouponPage page(String actor, String status, PageQuery pageQueryPage) {
    authorize(actor);
    java.time.LocalDateTime queryTime = now(clock);
    List<CouponProjection> couponProjectionListRows =
        couponMapper.selectCouponsByQuery(
            actor, status, queryTime, pageQueryPage.pageSize(), pageQueryPage.offset());
    CouponMapper.Counts counts = couponMapper.countByAvailability(actor, queryTime);
    long available = counts.available();
    long unavailable = counts.unavailable();
    long total =
        "all".equals(status)
            ? available + unavailable
            : TradingState.available.name().equals(status) ? available : unavailable;
    return new CouponPage(
        views(couponProjectionListRows),
        Long.toString(total),
        pageQueryPage.page(),
        pageQueryPage.pageSize(),
        Long.toString(available),
        Long.toString(unavailable));
  }

  /** 单券严格按本人读取。 */
  public Coupon detail(String actor, String id) {
    authorize(actor);
    CouponProjection couponProjection = couponMapper.selectCouponByUserAndId(actor, id, false);
    if (couponProjection == null) throw missing();
    return views(List.of(couponProjection)).get(0);
  }

  /** 统一批量模板范围展示，保留返还券原始类型与金额。 */
  public List<Coupon> views(List<CouponProjection> couponProjectionListRows) {
    Set<String> ids = new TreeSet<>();
    for (CouponProjection couponProjection : couponProjectionListRows)
      ids.add(couponProjection.templateId());
    Map<String, List<String>> scopes = new HashMap<>();
    if (!ids.isEmpty())
      for (CouponScopeProjection couponScopeProjectionScope :
          couponMapper.selectScopesByTemplateIds(new ArrayList<>(ids))) {
        List<String> factories = scopes.get(couponScopeProjectionScope.templateId());
        if (factories == null) {
          factories = new ArrayList<>();
          scopes.put(couponScopeProjectionScope.templateId(), factories);
        }
        factories.add(couponScopeProjectionScope.factoryId());
      }
    List<Coupon> couponListResult = new ArrayList<>();
    for (CouponProjection couponProjection : couponProjectionListRows)
      couponListResult.add(
          new Coupon(
              couponProjection.id(),
              couponProjection.userCouponNo(),
              couponProjection.templateName(),
              couponProjection.couponStatus(),
              couponProjection.couponTypeSnapshot(),
              couponProjection.faceAmountFenSnapshot(),
              couponProjection.discountRateBpsSnapshot(),
              couponProjection.thresholdRentAmountFenSnapshot(),
              couponProjection.scopeType(),
              List.copyOf(scopes.getOrDefault(couponProjection.templateId(), List.of())),
              time(couponProjection.validFrom()),
              time(couponProjection.validUntil()),
              time(couponProjection.returnedAt())));
    return List.copyOf(couponListResult);
  }

  /** 历史状态取usage自己的状态和确认时间。 */
  public PageResult<UseRecord> uses(String actor, PageQuery pageQueryPage) {
    authorize(actor);
    List<UseRecord> useRecordListResult = new ArrayList<>();
    for (CouponUseProjection couponUseProjection :
        couponMapper.selectUsesByUser(actor, pageQueryPage.pageSize(), pageQueryPage.offset()))
      useRecordListResult.add(
          new UseRecord(
              couponUseProjection.id(),
              couponUseProjection.couponId(),
              couponUseProjection.couponName(),
              couponUseProjection.batchId(),
              couponUseProjection.paymentId(),
              couponUseProjection.discountFen(),
              couponUseProjection.status(),
              time(couponUseProjection.usedAt()),
              time(couponUseProjection.returnedAt())));
    return PageResult.of(useRecordListResult, couponMapper.countUsesByUser(actor), pageQueryPage);
  }
}
