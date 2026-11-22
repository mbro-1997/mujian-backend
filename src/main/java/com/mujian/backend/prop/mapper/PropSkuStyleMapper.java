package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropSkuStyleDO;
import com.mujian.backend.prop.projection.PropStyleProjection;
import java.time.*;
import java.util.*;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PropSkuStyleMapper {
  /** 批量读取道具有序风格关联。 */
  List<PropStyleProjection> selectPropSkuStyleListBySkuIds(@Param("skuIds") List<String> skuIds);

  /** 保存道具风格关联。 */
  void insertPropSkuStyle(@Param("row") PropSkuStyleDO propSkuStyleDO);

  /** 按记录标识读取道具风格关联。 */
  PropSkuStyleDO selectPropSkuStyle(@Param("id") String id, @Param("lock") boolean lock);

  /** 在资料替换事务中移除旧风格关联。 */
  int deletePropSkuStylesBySkuId(@Param("skuId") String skuId);

  /** 按原顺序读取道具风格关联。 */
  List<PropSkuStyleDO> selectPropSkuStyleListBySkuId(@Param("sku") String sku);
}
