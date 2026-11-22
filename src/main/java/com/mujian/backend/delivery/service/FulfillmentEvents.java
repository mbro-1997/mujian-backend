package com.mujian.backend.delivery.service;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.domain.FulfillmentEventDO;
import com.mujian.backend.delivery.mapper.HandoverMapper;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 事实产生时同事务记一次事件，各身份只能回读授权订单的时间线。 */
@Service
public class FulfillmentEvents {
  private final HandoverMapper handoverMapper;
  private final FulfillmentAccess fulfillmentAccess;

  /** 复用原订单归属和管理员精确权限。 */
  public FulfillmentEvents(HandoverMapper handoverMapper, FulfillmentAccess fulfillmentAccess) {
    this.handoverMapper = handoverMapper;
    this.fulfillmentAccess = fulfillmentAccess;
  }

  /** 同一业务效果使用调用者明确的稳定键。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public String append(
      String key,
      String orderId,
      String legId,
      String sourceType,
      String sourceId,
      FulfillmentEventType type,
      LocalDateTime at,
      String actor,
      FulfillmentRole role,
      String summary) {
    return restore(null, key, orderId, legId, sourceType, sourceId, type, at, actor, role, summary);
  }

  /** 先核实稳定业务键，原审计已有事件号时恢复相同编号。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public String restore(
      String recordedId,
      String key,
      String orderId,
      String legId,
      String sourceType,
      String sourceId,
      FulfillmentEventType type,
      LocalDateTime at,
      String actor,
      FulfillmentRole role,
      String summary) {
    String existing = handoverMapper.eventId(orderId, key);
    if (existing != null) {
      if (recordedId != null && !recordedId.equals(existing))
        throw new IllegalStateException("return event reference changed");
      return existing;
    }
    String id = recordedId == null ? Ids.next() : recordedId;
    handoverMapper.insertEvent(
        new FulfillmentEventDO(
            id,
            key,
            orderId,
            legId,
            sourceType,
            sourceId,
            type.name(),
            at,
            actor,
            role.name(),
            summary));
    return id;
  }

  /** 鉴权后分页，事件与总数在同一读取视图。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public FulfillmentViews.EventPage page(
      String actor, String orderId, FulfillmentRole role, PageQuery pageQuery) {
    fulfillmentAccess.order(
        actor,
        orderId,
        role == FulfillmentRole.factory,
        role == FulfillmentRole.admin ? "admin:trade:order:read" : null);
    return new FulfillmentViews.EventPage(
        handoverMapper.selectEvents(orderId, pageQuery),
        pageQuery.page(),
        pageQuery.pageSize(),
        Long.toString(handoverMapper.countEvents(orderId)));
  }
}
