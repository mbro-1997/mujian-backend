package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropSnInstanceDO;
import com.mujian.backend.prop.dto.InstanceQueryDTO;
import com.mujian.backend.prop.dto.PropWriteDTO;
import com.mujian.backend.prop.projection.PropInstanceCountProjection;
import com.mujian.backend.prop.projection.PropQuantityProjection;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropSnInstanceMapper {

  /** 写入实物的新状态、版本和关联来源。 */
  int updatePropSnStatusByVersion(@Param("command") PropWriteDTO.TransitionSn transitionSn);

  /** 把实物绑定到新的业务二维码。 */
  int updatePropSnQrBinding(@Param("command") PropWriteDTO.BindSnQr bindSnQr);

  /** 保存实物资产。 */
  void insertPropSnInstance(@Param("row") PropSnInstanceDO propSnInstanceDO);

  /** 按已校验条件和分页读取实物资产记录。 */
  List<PropSnInstanceDO> selectPropSnInstancePageByQuery(
      @Param("instanceQueryDTO") InstanceQueryDTO instanceQueryDTO);

  /** 统计匹配条件的实物资产数量供分页。 */
  long selectPropSnInstanceCountByQuery(@Param("instanceQueryDTO") InstanceQueryDTO instanceQueryDTO);

  /** 按记录标识读取实物资产。 */
  PropSnInstanceDO selectPropSnInstance(@Param("id") String id, @Param("lock") boolean lock);

  /** 按租期读取实物占用情况供A类可租计算。 */
  List<PropQuantityProjection> selectRentableSnQuantityListBySkuIdsAndDates(
      @Param("skuIds") Collection<String> skuIds,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("today") LocalDate today);

  /** 统计指定道具各实物状态数量。 */
  List<PropInstanceCountProjection> selectPropSnStatusCountBySkuId(@Param("skuId") String skuId);

  /** 批量读取指定实物资产。 */
  List<PropSnInstanceDO> selectPropSnInstanceListByIds(@Param("ids") List<String> ids);

  /** 锁定可租SN，调用方已持有SKU根锁。 */
  List<String> selectAvailableSnIdsForUpdate(
      @Param("skuId") String skuId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("quantity") int quantity,
      @Param("today") LocalDate today);

  /** 加锁后按原版本更新当前订单和实物状态。 */
  int transitionFulfillment(
      @Param("id") String id,
      @Param("version") int version,
      @Param("status") String status,
      @Param("orderId") String orderId,
      @Param("actor") String actor,
      @Param("at") java.time.LocalDateTime at);
}
