package com.mujian.backend.planning.dto;

import com.mujian.backend.common.api.PageQuery;

/** 本人列表的明确查询条件。 */
public record PlanningQuery(String actor, String scope, PageQuery paging) {
  /** 复用公共分页的宽偏移量。 */
  public long offset() {
    return paging.offset();
  }

  /** 返回本页条数。 */
  public int limit() {
    return paging.pageSize();
  }
}
