package com.mujian.backend.coupon.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.coupon.projection.*;
import com.mujian.backend.coupon.vo.CouponViews.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

/** 券包、用券记录及结算选券查询。 */
public interface CouponReader {
  /** 合法历史查询不要求账号仍具备新增交易资格。 */
  void authorize(String actor);

  /** 统计不受当前分页截断影响。 */
  CouponPage page(String actor, String status, PageQuery pageQueryPage);

  /** 单券严格按本人读取。 */
  Coupon detail(String actor, String id);

  /** 统一批量模板范围展示，保留返还券原始类型与金额。 */
  List<Coupon> views(List<CouponProjection> couponProjectionListRows);

  /** 历史状态取usage自己的状态和确认时间。 */
  PageResult<UseRecord> uses(String actor, PageQuery pageQueryPage);
}
