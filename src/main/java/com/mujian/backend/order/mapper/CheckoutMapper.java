package com.mujian.backend.order.mapper;

import com.mujian.backend.order.projection.CheckoutFactoryProjection;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CheckoutMapper {
  /** 批量查询本次结算涉及的工厂及商户，不逐项查库。 */
  List<CheckoutFactoryProjection> selectFactoriesByIds(@Param("ids") List<String> ids);

  /** 新交易必须无未结清赔付。 */
  boolean existsOutstandingCompensation(@Param("actor") String actor);
}
