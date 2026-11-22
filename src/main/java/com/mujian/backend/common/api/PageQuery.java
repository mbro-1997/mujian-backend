package com.mujian.backend.common.api;

/** 同一分页规则用于HTTP绑定和服务调用；缺省与非法输入必须区分。 */
public record PageQuery(int page, int pageSize) {
  public PageQuery {
    if (page < 1 || pageSize < 1 || pageSize > 100) throw ApiFailure.invalid();
  }

  /** 校验并构造本类型所需数据。 */
  public static PageQuery of(Integer page, Integer pageSize) {
    return new PageQuery(page == null ? 1 : page, pageSize == null ? 20 : pageSize);
  }

  /** 解析并拒绝非法分页输入。 */
  public static int parse(String value, int fallback, int maximum) {
    if (value == null) return fallback;
    try {
      if (!value.matches("[1-9][0-9]*")) throw ApiFailure.invalid();
      int result = Integer.parseInt(value);
      if (result > maximum) throw ApiFailure.invalid();
      return result;
    } catch (NumberFormatException e) {
      throw ApiFailure.invalid();
    }
  }

  /** 使用长整型计算分页起点。 */
  public long offset() {
    return (page - 1L) * pageSize;
  }
}
