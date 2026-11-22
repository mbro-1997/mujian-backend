package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.coupon.vo.CouponViews;
import com.mujian.backend.order.domain.CheckoutQuoteData;
import com.mujian.backend.order.domain.CheckoutQuoteData.*;
import com.mujian.backend.order.dto.CheckoutDtos.*;
import com.mujian.backend.order.projection.*;
import com.mujian.backend.order.vo.CheckoutViews.*;
import com.mujian.backend.planning.domain.*;
import java.time.*;
import java.util.*;

/** 根据租期、配送及选券计算结算报价。 */
public interface CheckoutQuotation {
  public record Calculation(CheckoutQuoteData data, List<CouponViews.Choice> coupons) {}

  /** 两个只读入口共用完整计算，提交传入已验签凭据以保留预分配明细ID和有效路线。 */
  Calculation calculate(
      String actor, QuoteInput quoteInput, CheckoutQuoteData checkoutQuoteDataConfirmed);

  /** 签名只引用稳定资料，返回图片才进行有权限的临时签名。 */
  Quote quote(String actor, QuoteInput quoteInput);

  /** 候选券逐券给出真实节省，不改变用户已明确的选择。 */
  CouponViews.Choices coupons(String actor, QuoteInput quoteInput);
}
