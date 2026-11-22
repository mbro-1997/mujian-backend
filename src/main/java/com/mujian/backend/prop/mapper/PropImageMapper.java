package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropImageDO;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropImageMapper {

  /** 保存道具图片关联。 */
  void insertPropImage(@Param("row") PropImageDO propImageDO);

  /** 按记录标识读取道具图片关联。 */
  PropImageDO selectPropImage(@Param("id") String id, @Param("lock") boolean lock);

  /** 读取审核快照关联的道具图片。 */
  List<PropImageDO> selectSnapshotPropImageListBySkuId(@Param("skuId") String skuId);

  /** 在资料替换事务中移除旧图片关联。 */
  int deletePropImagesBySkuId(@Param("skuId") String skuId);

  /** 按原顺序读取道具图片关联。 */
  List<PropImageDO> selectPropImageListBySkuId(@Param("sku") String sku);

  /** 批量读取当前页道具图片，保留各道具原图片顺序。 */
  List<PropImageDO> selectPropImageListBySkuIds(@Param("skuIds") List<String> skuIds);
}
