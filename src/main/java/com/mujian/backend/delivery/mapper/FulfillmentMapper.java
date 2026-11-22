package com.mujian.backend.delivery.mapper;

import com.mujian.backend.delivery.domain.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FulfillmentMapper {
  /** 原出库根中是否已核对本SN，重复扫码不推进数量。 */
  boolean hasOutboundScan(@Param("rootId") String rootId, @Param("snId") String snId);

  /** 唯一成功扫描与进度在同一事务写入。 */
  void insertOutboundScan(@Param("row") OutboundScanDO row);

  /** 仅首次有效A类扫描加一且不能超过本单分配量。 */
  int incrementOutboundQuantity(
      @Param("rootId") String rootId,
      @Param("itemId") String itemId,
      @Param("actor") String actor,
      @Param("now") java.time.LocalDateTime now);

  /** 本单成功扫描分页，从数据库限定范围。 */
  java.util.List<com.mujian.backend.delivery.vo.FulfillmentViews.ScanRecord> selectOutboundScans(
      @Param("orderId") String orderId,
      @Param("query") com.mujian.backend.common.api.PageQuery query);

  /** 与扫描页相同订单范围统计。 */
  long countOutboundScans(@Param("orderId") String orderId);

  /** 首次草稿逐真实订单明细保存独立核对行。 */
  void insertOutboundItem(
      @Param("id") String id,
      @Param("rootId") String rootId,
      @Param("orderId") String orderId,
      @Param("itemId") String itemId,
      @Param("now") java.time.LocalDateTime now);

  /** 当前版本修改草稿备注，不修改已经确认的出库事实。 */
  int saveOutboundRemark(
      @Param("rootId") String rootId,
      @Param("version") int version,
      @Param("remark") String remark,
      @Param("actor") String actor,
      @Param("now") java.time.LocalDateTime now);

  /** 核对明细改变时统一推进出库根版本。 */
  int advanceOutboundVersion(
      @Param("rootId") String rootId,
      @Param("version") int version,
      @Param("actor") String actor,
      @Param("now") java.time.LocalDateTime now);

  /** B类设置核对总量而非累加，重试不增加数量。 */
  int setOutboundQuantity(
      @Param("rootId") String rootId,
      @Param("itemId") String itemId,
      @Param("quantity") int quantity,
      @Param("actor") String actor,
      @Param("now") java.time.LocalDateTime now);

  /** 读取冻结的两个运输段；缺失时不得派生为无需运输或无需资金处理。 */
  com.mujian.backend.delivery.projection.FulfillmentMetadata selectMetadataByOrder(
      @Param("orderId") String orderId);

  /** 按订单明细左关联真实核验，不因打开页面生成假草稿。 */
  java.util.List<com.mujian.backend.delivery.vo.FulfillmentViews.CheckItem> selectCheckItemsByOrder(
      @Param("orderId") String orderId, @Param("returning") boolean returning);

  /** 在订单根锁保护下读取真实子根，GET不创建记录。 */
  OrderOutboundDO selectOrderOutboundDOByOrder(@Param("orderId") String orderId);

  /** 保存已锁定订单的唯一出库根。 */
  void insertOutbound(@Param("row") OrderOutboundDO orderOutboundDO);

  /** 在订单根锁保护下读取真实子根，GET不创建记录。 */
  OrderReceiptDO selectOrderReceiptDOByOrder(@Param("orderId") String orderId);

  /** 在订单根锁保护下读取真实子根，GET不创建记录。 */
  OrderReturnShipmentDO selectOrderReturnShipmentDOByOrder(@Param("orderId") String orderId);

  /** 在订单根锁保护下读取真实子根，GET不创建记录。 */
  OrderReturnCheckDO selectOrderReturnCheckDOByOrder(@Param("orderId") String orderId);
}
