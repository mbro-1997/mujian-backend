package com.mujian.backend.delivery.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReturnCheckMapper {
  /** 同单唯一处置根，所有写入口都在原业务锁之后取得。 */
  OrderReturnCheckDO lockCheck(@Param("orderId") String orderId);

  /** 真实到厂或预计时间到达后创建核验。 */
  void insertCheck(@Param("row") OrderReturnCheckDO row);

  /** 每笔归还核验明细固定原出库数量。 */
  void insertItem(
      @Param("id") String id,
      @Param("checkId") String checkId,
      @Param("itemId") String itemId,
      @Param("quantity") int quantity,
      @Param("at") LocalDateTime at);

  /** B类设置/首次A类计数后的明确总量。 */
  int setQuantity(
      @Param("checkId") String checkId,
      @Param("itemId") String itemId,
      @Param("quantity") int quantity,
      @Param("actor") String actor,
      @Param("at") LocalDateTime at);

  /** 单次核对或实际到厂推进根版本。 */
  int advanceReturnCheckVersion(
      @Param("id") String id,
      @Param("version") int version,
      @Param("actor") String actor,
      @Param("at") LocalDateTime at);

  /** 只允许将未来的计划起点提前。 */
  int updateReturnCheckStartAndDeadlines(
      @Param("id") String id,
      @Param("start") LocalDateTime start,
      @Param("source") String source,
      @Param("at") LocalDateTime at);

  /** 标记一次超时预警。 */
  int markReturnCheckWarned(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 工厂实查、自动/管理规则关闭分别留存操作者和事实。 */
  int closeReturnCheck(
      @Param("id") String id,
      @Param("version") int version,
      @Param("status") String status,
      @Param("reasonCode") String reasonCode,
      @Param("reason") String reason,
      @Param("actor") String actor,
      @Param("at") LocalDateTime at);

  /** 扫过同一实物不重复生成成功记录。 */
  boolean hasScan(@Param("checkId") String checkId, @Param("snId") String snId);

  /** 归还只核对原实际出库SN。 */
  String selectDispatchedItem(@Param("orderId") String orderId, @Param("snId") String snId);

  /** 只写一次成功归还扫码。 */
  void insertScan(@Param("row") OutboundScanDO row);

  /** 成功扫码历史归属限定在本订单。 */
  List<FulfillmentViews.ScanRecord> selectScans(
      @Param("orderId") String orderId, @Param("query") PageQuery query);

  /** 归还扫码分页总数。 */
  long countScans(@Param("orderId") String orderId);

  /** 只认本单实际已完成的返程。 */
  boolean returnLegComplete(@Param("orderId") String orderId);

  /** 回程完成来自核验关闭，若先到厂保留真实发出时刻为空。 */
  int finishReturnLeg(@Param("orderId") String orderId, @Param("at") LocalDateTime at);

  /** 返回实际释放的本单占用编号供管理执行步骤追溯。 */
  List<String> releasedIds(@Param("orderId") String orderId);

  /** 扫描持久截止及缺失核验根，按订单跨页避免饥饿。 */
  List<String> selectDueOrders(@Param("at") LocalDateTime at, @Param("afterId") String afterId);
}
