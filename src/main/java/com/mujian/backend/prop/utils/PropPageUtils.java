package com.mujian.backend.prop.utils;

import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.prop.dto.PropPageFilterDTO;
import java.util.List;

public final class PropPageUtils {
  /** 限制工具类实例化，统一通过静态方法使用。 */
  private PropPageUtils() {}

  /** 按既定接口格式封装分页数据和总数。 */
  public static <T> PageResult<T> page(
      List<T> items, long total, PropPageFilterDTO propPageFilterDTO) {
    return new PageResult<>(
        items,
        Long.toString(total),
        number(propPageFilterDTO, "page", 1),
        number(propPageFilterDTO, "pageSize", 20));
  }

  /** 读取并校验分页整数，缺省时使用约定默认值。 */
  public static int number(PropPageFilterDTO propPageFilterDTO, String k, int d) {
    return com.mujian.backend.common.api.PageQuery.parse(
        ("pageSize".equals(k) ? propPageFilterDTO.getPageSize() : propPageFilterDTO.getPage()),
        d,
        "pageSize".equals(k) ? 100 : Integer.MAX_VALUE);
  }

  /** 使用long计算分页偏移，避免整数乘法溢出。 */
  public static long offset(PropPageFilterDTO propPageFilterDTO) {
    return (number(propPageFilterDTO, "page", 1) - 1L) * number(propPageFilterDTO, "pageSize", 20);
  }
}
