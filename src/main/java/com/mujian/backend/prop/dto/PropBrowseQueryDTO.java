package com.mujian.backend.prop.dto;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.common.support.D2Values;
import java.util.*;

/** 三个列表入口共用的明确条件；权限由对应Service入口决定。 */
public record PropBrowseQueryDTO(
    Integer page,
    Integer pageSize,
    String typeCode,
    List<String> styleCodes,
    String assetType,
    String keyword,
    String startDate,
    String endDate) {
  /** 构造当前数据对象并校验参数边界。 */
  public PropBrowseQueryDTO {
    PageQuery pageQuery = PageQuery.of(page, pageSize);
    page = pageQuery.page();
    pageSize = pageQuery.pageSize();
    if (keyword != null) keyword = D2Values.text(keyword, 128);
    if (assetType != null) {
      try {
        com.mujian.backend.common.enums.PropAssetType.valueOf(assetType);
      } catch (IllegalArgumentException illegalArgumentException) {
        throw ApiFailure.invalid();
      }
    }
    if (typeCode != null && !typeCode.matches("[!-~]{1,32}")) throw ApiFailure.invalid();
    styleCodes = com.mujian.backend.prop.policy.PropStyleFilter.validate(styleCodes);
  }

  /** 返回已校验的分页条件供列表查询使用。 */
  public PageQuery paging() {
    return new PageQuery(page, pageSize);
  }

  /** 按long计算分页偏移，避免页码乘法溢出。 */
  public long offset() {
    return paging().offset();
  }
}
