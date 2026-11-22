package com.mujian.backend.order.mapper;

import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.FormationWrites;
import com.mujian.backend.order.dto.OrderQuery;
import com.mujian.backend.order.dto.OrderWrites;
import com.mujian.backend.order.projection.OrderMetadataProjection;
import com.mujian.backend.order.projection.OrderOverviewProjection;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RentalOrderMapper {
  /** 接单同事务将冻结运输段置为可执行，兼容D3已接单尚未转换的记录。 */
  int readyTransportLegs(@Param("orderId") String orderId, @Param("now") LocalDateTime now);

  /** 按买家或工厂范围筛选并分页，避免同一订单重复计入。 */
  List<com.mujian.backend.order.projection.RentalOrderListProjection> selectOrdersByQuery(
      @Param("query") OrderQuery orderQuery,
      @Param("at") LocalDateTime at,
      @Param("ready") boolean ready,
      @Param("blocked") java.util.Set<String> blocked);

  /** 沿列表相同范围计算总数，不受当前页大小影响。 */
  long countOrdersByQuery(
      @Param("query") OrderQuery orderQuery,
      @Param("at") LocalDateTime at,
      @Param("ready") boolean ready,
      @Param("blocked") java.util.Set<String> blocked);

  /** 批量补齐本页工厂名称、配送状态和明细数量。 */
  List<OrderMetadataProjection> selectOrderMetadata(
      @Param("ids") List<String> ids,
      @Param("at") LocalDateTime at,
      @Param("settlements") boolean settlements);

  /** A独立部署时不引用B的结算表；B就绪后才开放到账记录入口。 */
  boolean hasSettlementTables();

  /** 按原工厂归属统计全部待办，不设置展示条数上限。 */
  OrderOverviewProjection selectFactoryOrderOverview(
      @Param("id") String factoryId, @Param("at") LocalDateTime at);

  /** 订单根读取；调用者先做归属鉴权，统一根服务负责锁序。 */
  RentalOrderDO selectOrderById(@Param("id") String id);

  /** 核对履约是否尚未出库，供取消状态保护。 */
  String selectOrderDeliveryStatus(@Param("id") String id);

  /** 原版本且仍待接单时确认接单，不重新选取库存。 */
  int acceptOrder(
      @Param("id") String id, @Param("version") int version, @Param("now") LocalDateTime now);

  /** 条件更新原订单终止事实，与退款义务同事务。 */
  int terminateOrder(@Param("termination") OrderWrites.Termination termination);

  /** 记录按原租金计算的扣款及当时规则快照。 */
  void insertCancellationDeduction(@Param("deduction") OrderWrites.Deduction deduction);

  /** 终止未履约订单的配送安排，保留原地址快照。 */
  void cancelOrderDelivery(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 同步终止原订单往返段，保留原报价快照。 */
  void cancelTransportLegs(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 关闭原运费待执行义务，资金退款仍按原科目处理。 */
  void cancelFreight(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 有限扫描已到接单截止的原订单供后台恢复。 */
  List<String> selectAcceptanceTimeoutOrderIds(@Param("now") LocalDateTime now);

  /** 成单主记录来源全部为冻结快照和真实付款时刻。 */
  void insertOrder(@Param("order") FormationWrites.Order order);

  /** 每个原快照只生成一个订单明细。 */
  void insertItemFromSnapshot(@Param("item") FormationWrites.Item item);

  /** 配送摘要进入待出库，D3不写实际履约事实。 */
  void insertDelivery(@Param("delivery") FormationWrites.Delivery delivery);

  /** 运费账户按原预收建立。 */
  void insertFreight(@Param("freight") FormationWrites.Freight freight);

  /** 原子单科目分摊与订单同事务。 */
  void insertPaymentAllocation(@Param("allocation") FormationWrites.Allocation allocation);

  /** 每厂保留两个完整方向，非预收路线字段保持NULL。 */
  void insertTransportLeg(@Param("leg") FormationWrites.Leg leg);

  /** 原子单只补该批次形成的正式订单。 */
  void bindSuborder(
      @Param("id") String id, @Param("orderId") String orderId, @Param("now") LocalDateTime now);

  /** 批次后按订单ID顺序锁已有订单，空结果由批次根串行保护。 */
  List<String> selectOrderIdsByBatch(@Param("batch") String batch, @Param("lock") boolean lock);
}
