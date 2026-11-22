package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.dto.OwnedQueryDTO;
import com.mujian.backend.prop.dto.PropBrowseQueryDTO;
import com.mujian.backend.prop.dto.PropWriteDTO;
import com.mujian.backend.prop.projection.PropBrowseProjection;
import com.mujian.backend.prop.projection.PropInventorySummaryProjection;
import java.time.*;
import java.util.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropSkuMapper {
  /** 统计匹配条件的可见道具数量，供普通列表分页。 */
  long selectPropCountByQuery(@Param("query") PropBrowseQueryDTO propBrowseQueryDTO);

  /** 按数据库排序和分页条件读取道具卡片投影。 */
  List<PropBrowseProjection> selectPropPageByQuery(
      @Param("query") PropBrowseQueryDTO propBrowseQueryDTO);

  /** 读取全部匹配候选，供租期全局可租排序后再分页。 */
  List<PropBrowseProjection> selectPropCandidateListByQuery(
      @Param("query") PropBrowseQueryDTO propBrowseQueryDTO);

  /** 写入已批准的道具资料，保留可空字段语义。 */
  void updatePropSkuFields(@Param("row") PropSkuDO propSkuDO);

  /** 按版本写入道具类型、风格相关选项及运输建议。 */
  int updatePropSkuOptionsByVersion(
      @Param("command") PropWriteDTO.UpdateSkuOptions updateSkuOptions);

  /** 写入审核通过后的道具上下架状态。 */
  int updatePropSkuStatusByVersion(@Param("command") PropWriteDTO.ChangeSkuStatus changeSkuStatus);

  /** 保存道具主资料。 */
  void insertPropSku(@Param("row") PropSkuDO propSkuDO);

  /** 按已校验条件和分页读取工厂道具记录。 */
  List<PropSkuDO> selectOwnedPropSkuPageByQuery(@Param("ownedQueryDTO") OwnedQueryDTO ownedQueryDTO);

  /** 统计匹配条件的工厂道具数量供分页。 */
  long selectOwnedPropSkuCountByQuery(@Param("ownedQueryDTO") OwnedQueryDTO ownedQueryDTO);

  /** 按记录标识读取道具主资料。 */
  PropSkuDO selectPropSku(@Param("id") String id, @Param("lock") boolean lock);

  /** 批量读取工厂道具的库存与工厂摘要。 */
  List<PropInventorySummaryProjection> selectPropInventorySummaryListBySkuIds(
      @Param("ids") List<String> ids, @Param("today") java.time.LocalDate today);

  /** 按本页标识批量读取PropSkuDO，复用审核条件所需的当前数据。 */
  List<PropSkuDO> selectPropSkuListByIds(@Param("ids") List<String> ids);

  /** 批量读取方案清单所需工厂归属与公开性。 */
  List<com.mujian.backend.prop.projection.PropPlanningProjection> selectPlanningFactsBySkuIds(
      @Param("ids") List<String> ids);
}
