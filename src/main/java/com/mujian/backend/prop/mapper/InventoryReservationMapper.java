package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.InventoryReservationDO;
import com.mujian.backend.prop.dto.ReservationQueryDTO;
import com.mujian.backend.prop.projection.PropReservationProjection;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InventoryReservationMapper {
  /** 原批次热占用用于资金状态转换，终态归档不参与成单。 */
  List<InventoryReservationDO> selectOrderReservationsIncludingHistory(@Param("orderId") String orderId);

  /** 按批次取得原分配以锁定库存。 */
  List<InventoryReservationDO> selectReservationsByBatch(@Param("id") String id);

  /** 按已校验条件和分页读取库存占用记录。 */
  List<PropReservationProjection> selectInventoryReservationPageByQuery(
      @Param("reservationQueryDTO") ReservationQueryDTO reservationQueryDTO);

  /** 统计匹配条件的库存占用数量供分页。 */
  long selectInventoryReservationCountByQuery(@Param("reservationQueryDTO") ReservationQueryDTO reservationQueryDTO);

  /** 按记录标识读取库存占用记录。 */
  InventoryReservationDO selectInventoryReservation(
      @Param("id") String id, @Param("lock") boolean lock);

  /** 保存已在库存根锁下核实的占用。 */
  void insertReservation(@Param("row") InventoryReservationDO row);

  /** 全付款成单后转换原短锁，不创建新占用。 */
  int confirmPaid(
      @Param("batchId") String batchId,
      @Param("snapshotId") String snapshotId,
      @Param("orderId") String orderId,
      @Param("orderItemId") String orderItemId,
      @Param("now") LocalDateTime now);

  /** 关闭确认后释放该批次尚未转单的短锁。 */
  int releaseBatch(
      @Param("batchId") String batchId,
      @Param("status") String status,
      @Param("now") LocalDateTime now);

  /** 订单取消事务中释放对应正式占用。 */
  int releaseOrder(@Param("orderId") String orderId, @Param("now") LocalDateTime now);

  /** 归档只锁结束超过六个月的小批热记录。 */
  List<InventoryReservationDO> archiveCandidates(@Param("before") LocalDateTime before);

  /** 复制完整原记录到历史，原ID具有唯一键。 */
  void copyHistory(
      @Param("id") String id,
      @Param("origin") String origin,
      @Param("batch") String batch,
      @Param("now") LocalDateTime now);

  /** 原ID读取历史用于完整复制校验。 */
  InventoryReservationDO selectInventoryReservationHistoryByOrigin(@Param("origin") String origin);

  /** 核验完成后删除仍为结束状态的原记录。 */
  int deleteArchived(@Param("id") String id, @Param("version") int version);

  /** 实际交接只转换本单原占用，提前出库从实际日开始占用。 */
  int transitionFulfillment(
      @Param("id") String id,
      @Param("orderId") String orderId,
      @Param("before") String before,
      @Param("outbound") boolean outbound,
      @Param("at") java.time.LocalDateTime at);

  /** 早出库的新增时间段仍须核对同SN其它活动分配。 */
  boolean hasOtherSnOccupancy(
      @Param("snId") String snId,
      @Param("id") String id,
      @Param("start") java.time.LocalDate start,
      @Param("end") java.time.LocalDate end,
      @Param("today") java.time.LocalDate today);
}
