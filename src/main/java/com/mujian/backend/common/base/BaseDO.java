package com.mujian.backend.common.base;

/** 只适用于以字符串ID为主键的持久化实体，不携带查询条件或假定的审计字段。 */
public abstract class BaseDO {
  private String id;

  /** 返回数据库记录标识。 */
  public String getId() {
    return id;
  }

  /** 保存数据库记录标识。 */
  public void setId(String id) {
    this.id = id;
  }
}
