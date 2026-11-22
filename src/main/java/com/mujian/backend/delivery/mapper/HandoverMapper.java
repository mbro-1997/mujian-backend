package com.mujian.backend.delivery.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.delivery.domain.FulfillmentEventDO;
import com.mujian.backend.delivery.domain.OrderReceiptDO;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface HandoverMapper {
  /** 锁内重数有效出库照片，草稿已移除及无原始凭证记录不计。 */
  int countOutboundPhotos(@Param("orderId") String orderId);

  /** A类实际成功SN数与核对数量均须齐全。 */
  boolean hasIncompleteOutbound(@Param("orderId") String orderId);

  /** 按预期版本完成原出库记录。 */
  int completeOutbound(
      @Param("id") String id,
      @Param("version") int version,
      @Param("photos") int photos,
      @Param("actor") String actor,
      @Param("at") LocalDateTime at);

  /** 原订单状态条件推进，与取消/拒单互斥。 */
  int changeOrderState(
      @Param("orderId") String orderId,
      @Param("before") String before,
      @Param("after") String after,
      @Param("at") LocalDateTime at);

  /** 运输段发出时间只在原ready段写一次。 */
  int dispatchLeg(
      @Param("id") String id, @Param("orderId") String orderId, @Param("at") LocalDateTime at);

  /** 运输段完成时间独立记录。 */
  int completeLeg(
      @Param("id") String id, @Param("orderId") String orderId, @Param("at") LocalDateTime at);

  /** 固化去程交接及收货核验起止，实际确认时间由收货根保存。 */
  int dispatchDelivery(@Param("receipt") OrderReceiptDO receipt, @Param("at") LocalDateTime at);

  /** 创建本单唯一收货根。 */
  void insertReceipt(@Param("receipt") OrderReceiptDO receipt);

  /** 收货人工与自动共用同一CAS更新。 */
  int confirmReceipt(
      @Param("id") String id,
      @Param("version") int version,
      @Param("status") String status,
      @Param("at") LocalDateTime at);

  /** 更新原交接记录已收货。 */
  int receiveDelivery(@Param("orderId") String orderId, @Param("at") LocalDateTime at);

  /** 仅扫描持久的待确认且到期记录。 */
  List<String> selectDueReceipts(@Param("at") LocalDateTime at, @Param("afterId") String afterId);

  /** 在订单根锁下复用已持久化的同一事实。 */
  String eventId(@Param("orderId") String orderId, @Param("key") String key);

  /** 本地事件与原事实在一个事务中提交。 */
  void insertEvent(@Param("event") FulfillmentEventDO event);

  /** 分页查看单订单事件。 */
  List<FulfillmentViews.Event> selectEvents(
      @Param("orderId") String orderId, @Param("query") PageQuery query);

  /** 与分页共用订单归属范围。 */
  long countEvents(@Param("orderId") String orderId);
}
