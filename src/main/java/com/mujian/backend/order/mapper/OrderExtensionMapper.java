package com.mujian.backend.order.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderExtensionMapper {
  record QuoteBasis(long dailyRentFen, Integer snLimitDays, boolean renting) {}

  /** 从原单价和全部具体实物占用区间计算上限；B 类数量不降低延期天数。 */
  QuoteBasis selectExtensionQuoteBasis(@Param("orderId") String orderId,@Param("today") LocalDate today);
}
