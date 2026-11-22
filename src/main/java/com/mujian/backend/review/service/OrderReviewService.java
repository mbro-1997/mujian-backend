package com.mujian.backend.review.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.FirstRewardEvent;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.review.dto.ReviewInput;
import com.mujian.backend.review.mapper.ReviewMapper;
import com.mujian.backend.review.projection.ReviewProjection;
import com.mujian.backend.review.projection.ReviewProjection.*;
import com.mujian.backend.review.vo.ReviewViews.*;
import com.mujian.backend.reward.service.FirstRewardCaptureService;
import com.mujian.backend.web.response.account.ContextResponse;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class OrderReviewService {
  private final ReviewMapper reviewMapper;
  private final FactoryAccess factoryAccess;
  private final FirstRewardCaptureService firstRewardCaptureService;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  /** 复用会话身份、订单根和首次奖励事实。 */
  public OrderReviewService(
      ReviewMapper reviewMapper,
      FactoryAccess factoryAccess,
      FirstRewardCaptureService firstRewardCaptureService,
      Clock clock,
      ObjectMapper objectMapper) {
    this.reviewMapper = reviewMapper;
    this.factoryAccess = factoryAccess;
    this.firstRewardCaptureService = firstRewardCaptureService;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  /** 只允许本租赁方或本工厂作为评价主体。 */
  private Actor actor(ContextResponse context) {
    String user = context.userId();
    if (D2Codes.CODE_DEMANDER.equals(context.roleCode())) return new Actor(user, null, FulfillmentRole.buyer.name());
    if (D2Codes.CODE_SUPPLIER.equals(context.roleCode()))
      return new Actor(user, factoryAccess.requireOwnedFactoryId(user), FulfillmentRole.factory.name());
    throw ApiFailure.denied(D2Codes.CODE_PERMISSION_DENIED);
  }

  /** 归属从业务订单读取，提交时与售后办理锁同一订单。 */
  private Order ownedOrder(String type, String id, Actor actor, boolean lock) {
    if (!Set.of("rental_order", "custom_order").contains(type)) throw ApiFailure.invalid();
    Order order = reviewMapper.selectOrder(type, id(id), lock);
    if (order == null
        || !(FulfillmentRole.buyer.name().equals(actor.role())
            ? actor.userId().equals(order.buyerUserId())
            : actor.factoryId().equals(order.factoryId()))) throw missing();
    return order;
  }

  /** 四组标签对应正式 07 页面。 */
  private List<String> tags(String type, Actor actor) {
    if ("custom_order".equals(type))
      return FulfillmentRole.buyer.name().equals(actor.role())
          ? List.of("符合报价", "做工质量", "交付时间", "沟通")
          : List.of("需求清晰度", "确认效率", "沟通配合");
    return FulfillmentRole.buyer.name().equals(actor.role())
        ? List.of("道具状态", "交付效率", "包装", "沟通")
        : List.of("归还时间", "道具状态", "沟通配合");
  }

  /** 只投影当前评价关联的本人奖励。 */
  private Review view(ReviewProjection reviewProjection) {
    return new Review(
        reviewProjection.id(),
        reviewProjection.businessType(),
        reviewProjection.businessOrderId(),
        reviewProjection.reviewerRole(),
        reviewProjection.rating(),
        reviewProjection.tagJson() == null
            ? List.of()
            : List.of(objectMapper.readValue(reviewProjection.tagJson(), String[].class)),
        reviewProjection.content(),
        reviewProjection.status(),
        time(reviewProjection.createdAt()),
        reviewProjection.rewardStatus(),
        reviewProjection.userCouponId());
  }

  /** 返回真实可评条件与既有评价入口。 */
  public Entry entry(String user, String type, String id) {
    ContextResponse context = factoryAccess.context(user, false);
    Actor actor = actor(context);
    Order order = ownedOrder(type, id, actor, false);
    ReviewProjection original = reviewMapper.selectByOrderRole(type, id, actor.role(), actor);
    String reason = null;
    if (original != null) reason = BusinessFailureReason.REVIEW_ALREADY_SUBMITTED.name();
    else if (!"completed".equals(order.orderStatus()) || reviewMapper.hasOpenDispute(type, id))
      reason = BusinessFailureReason.ORDER_NOT_REVIEWABLE.name();
    else if (!D2Codes.CODE_ACTIVE.equals(context.userStatus()))
      reason = D2Codes.CODE_ACCOUNT_ACTION_RESTRICTED;
    return new Entry(
        reason == null,
        reason,
        original != null && "published".equals(original.status()) ? original.id() : null,
        tags(type, actor));
  }

  /** 锁内先还原本方向结果，再判断新评价资格。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Review submit(String user, ReviewInput reviewInput) {
    factoryAccess.lock(user);
    Actor actor = actor(factoryAccess.context(user, true));
    String type = reviewInput.businessType(), orderId = reviewInput.businessOrderId();
    Order order = ownedOrder(type, orderId, actor, true);
    List<String> selected = reviewInput.tags() == null ? List.of() : reviewInput.tags();
    List<String> allowed = tags(type, actor);
    if (reviewInput.rating() < 1
        || reviewInput.rating() > 5
        || !allowed.containsAll(selected)
        || new HashSet<>(selected).size() != selected.size()) throw ApiFailure.invalid();
    List<String> normalized = new ArrayList<>(allowed);
    normalized.retainAll(selected);
    ReviewProjection original = reviewMapper.selectByOrderRole(type, orderId, actor.role(), actor);
    if (original != null) {
      if (!"published".equals(original.status())) throw conflict(BusinessFailureReason.REVIEW_ALREADY_SUBMITTED.name());
      Review previous = view(original);
      if (previous.rating() == reviewInput.rating()
          && previous.tags().equals(normalized)
          && Objects.equals(previous.content(), reviewInput.content())) return previous;
      throw conflict(BusinessFailureReason.REVIEW_ALREADY_SUBMITTED.name());
    }
    if (!"completed".equals(order.orderStatus()) || reviewMapper.hasOpenDispute(type, orderId))
      throw conflict(BusinessFailureReason.ORDER_NOT_REVIEWABLE.name());
    String reviewId = Ids.next();
    LocalDateTime at = now(clock);
    reviewMapper.insertReview(
        reviewId,
        reviewInput,
        actor,
        FulfillmentRole.buyer.name().equals(actor.role()) ? order.factoryId() : order.buyerUserId(),
        objectMapper.writeValueAsString(normalized),
        at);
    if (FulfillmentRole.buyer.name().equals(actor.role()) && "rental_order".equals(type))
      firstRewardCaptureService.capture(
          FirstRewardEvent.first_rental_review, user, reviewId, true, true, at);
    return view(reviewMapper.selectVisibleById(reviewId, actor));
  }

  /** 按当前角色分页查询给出或收到的评价。 */
  public PageResult<Review> page(String user, String type, String direction, PageQuery page) {
    Actor actor = actor(factoryAccess.context(user, false));
    long count = reviewMapper.countVisible(actor, type, direction);
    List<Review> items = new ArrayList<>();
    if (count > page.offset())
      for (ReviewProjection reviewProjection : reviewMapper.selectVisiblePage(actor, type, direction, page))
        items.add(view(reviewProjection));
    return PageResult.of(items, count, page);
  }

  /** 其他订单参与者无法通过评价 ID 读出内容。 */
  public Review detail(String user, String id) {
    ReviewProjection reviewProjection = reviewMapper.selectVisibleById(id(id), actor(factoryAccess.context(user, false)));
    if (reviewProjection == null) throw missing();
    return view(reviewProjection);
  }
}
