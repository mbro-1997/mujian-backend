package com.mujian.backend.common.api;

import java.util.List;

/** total沿用现有JSON十进制字符串，不套用若依TableDataInfo。 */
public record PageResult<T>(List<T> items, String total, int page, int pageSize) {
  /** 校验并构造本类型所需数据。 */
  public static <T> PageResult<T> of(List<T> items, long total, PageQuery query) {
    return new PageResult<>(
        List.copyOf(items), Long.toString(total), query.page(), query.pageSize());
  }
}
