package com.mujian.backend.delivery.mapper;

import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.dto.TransportQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TransportMapper {
  /** 查询车型根，锁定后串行处理其发布价格范围。 */
  VehicleDO selectVehicleById(@Param("id") String id, @Param("lock") boolean lock);

  /** 分页查询车型。 */
  List<VehicleDO> selectVehiclesByQuery(@Param("transportQuery") TransportQuery transportQuery);

  /** 统计车型。 */
  long countVehiclesByQuery(@Param("transportQuery") TransportQuery transportQuery);

  /** 查询可用于自动匹配的全部启用车型。 */
  List<VehicleDO> selectEnabledVehicles();

  /** 新增车型。 */
  void insertVehicle(@Param("row") VehicleDO vehicleDO);

  /** 条件更新车型，不覆盖旧版本。 */
  int updateVehicle(@Param("row") VehicleDO vehicleDO, @Param("expected") int expected);

  /** 查询单条规则并按需加锁。 */
  PriceRuleDO selectPriceRuleById(@Param("id") String id, @Param("lock") boolean lock);

  /** 分页查询规则。 */
  List<PriceRuleDO> selectPriceRulesByQuery(
      @Param("transportQuery") TransportQuery transportQuery, @Param("now") LocalDateTime now);

  /** 统计规则。 */
  long countPriceRulesByQuery(
      @Param("transportQuery") TransportQuery transportQuery, @Param("now") LocalDateTime now);

  /** 当前价格单不返回未发布或失效规则。 */
  List<PriceRuleDO> selectActivePriceRules(
      @Param("now") LocalDateTime now, @Param("cityCode") String cityCode);

  /** 查询同车型城市中时间及距离同时重叠的已发布规则。 */
  long countOverlappingPriceRules(@Param("row") PriceRuleDO priceRuleDO);

  /** 新增草稿规则。 */
  void insertRule(@Param("row") PriceRuleDO priceRuleDO);

  /** 在原版本上保存草稿、发布或结束时间。 */
  int updateRule(@Param("row") PriceRuleDO priceRuleDO, @Param("expected") int expected);
}
