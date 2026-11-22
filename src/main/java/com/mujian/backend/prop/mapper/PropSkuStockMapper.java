package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropSkuStockDO;
import com.mujian.backend.prop.projection.PropQuantityProjection;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropSkuStockMapper {
  /** 读取指定业务日期的真实日占用，不能沿用上次写入日的缓存。 */
  com.mujian.backend.prop.projection.CurrentStockQuantityProjection selectCurrentStockQuantities(
      @Param("sku") String sku, @Param("today") LocalDate today);

  /** 未截零的同一峰值余额，用于已付订单供货风险提示。 */
  List<PropQuantityProjection> selectStockBalanceBySkuIdsAndDates(
      @Param("skuIds") Collection<String> skuIds,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("today") LocalDate today);

  /** 在SKU及库存根锁内刷新当前日经营看板，不作为未来租期基数。 */
  void refreshCurrentQuantities(
      @Param("sku") String sku, @Param("today") LocalDate today, @Param("now") LocalDateTime now);

  /** 按批准后的数值更新库存台账及版本。 */
  int updatePropSkuStockByVersion(@Param("row") PropSkuStockDO propSkuStockDO);

  /** 保存数量型库存台账。 */
  void insertPropSkuStock(@Param("row") PropSkuStockDO propSkuStockDO);

  /** 按记录标识读取数量型库存台账。 */
  PropSkuStockDO selectPropSkuStock(@Param("id") String id, @Param("lock") boolean lock);

  /** 按道具标识读取唯一库存台账并按需加锁。 */
  List<PropSkuStockDO> selectPropSkuStockListBySkuId(
      @Param("skuId") String skuId, @Param("lock") boolean lock);

  /** 按租期读取库存占用区间供B类峰值计算。 */
  List<PropQuantityProjection> selectStockOccupancyListBySkuIdsAndDates(
      @Param("skuIds") Collection<String> skuIds,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("today") LocalDate today);
}
