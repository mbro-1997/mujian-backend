package com.mujian.backend.delivery.dto;

import com.mujian.backend.common.api.PageQuery;

/** 已验证的列表过滤与分页，金额配置不使用公共商品缓存。 */
public record TransportQuery(
    PageQuery page, String keyword, String status, String cityCode, String vehicleTierId) {
  /** 计算数据库分页偏移。 */
  public long offset() {
    return page.offset();
  }

  /** 取得本页条数。 */
  public int limit() {
    return page.pageSize();
  }
}
