package com.mujian.backend.address.mapper;

import com.mujian.backend.address.domain.ShippingAddressDO;
import com.mujian.backend.planning.dto.PlanningQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AddressMapper {

  /** 读取本人地址，含软删记录供原请求授权恢复。 */
  ShippingAddressDO selectOwnedAddressById(
      @Param("actor") String actor, @Param("id") String id, @Param("lock") boolean lock);

  /** 统计本人有效地址。 */
  long selectAddressCountByActor(@Param("actor") String actor);

  /** 默认优先、最近更新分页。 */
  List<ShippingAddressDO> selectAddressPageByQuery(@Param("query") PlanningQuery planningQuery);

  /** 写入用户明确选择默认状态的新地址。 */
  void insertAddress(@Param("row") ShippingAddressDO shippingAddressDO);

  /** 保存地址当前资料或软删状态。 */
  void updateAddress(@Param("row") ShippingAddressDO shippingAddressDO);

  /** 在用户根锁内释放其他默认地址，并推进它们的版本。 */
  void clearOtherAddressDefaultsByActor(
      @Param("actor") String actor,
      @Param("exceptId") String exceptId,
      @Param("now") LocalDateTime now);
}
