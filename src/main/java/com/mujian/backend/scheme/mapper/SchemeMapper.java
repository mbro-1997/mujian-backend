package com.mujian.backend.scheme.mapper;

import com.mujian.backend.planning.dto.PlanningQuery;
import com.mujian.backend.scheme.domain.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SchemeMapper {

  /** 查询本人方案，归档仍可读取。 */
  SchemeDO selectOwnedSchemeById(
      @Param("actor") String actor, @Param("id") String id, @Param("lock") boolean lock);

  /** 按ID读取分享对应方案。 */
  SchemeDO selectSchemeById(@Param("id") String id);

  /** 统计本人筛选后的方案。 */
  long selectSchemeCountByQuery(@Param("query") PlanningQuery planningQuery);

  /** 按最近更新分页读取本人方案。 */
  List<SchemeDO> selectSchemePageByQuery(@Param("query") PlanningQuery planningQuery);

  /** 读取本人已占用的编辑槽位。 */
  List<Integer> selectSchemeActiveSlotsByActor(@Param("actor") String actor);

  /** 保存新方案根。 */
  void insertScheme(@Param("row") SchemeDO schemeDO);

  /** 保存元信息、状态和版本。 */
  void updateScheme(@Param("row") SchemeDO schemeDO);

  /** 批量读取指定方案成员，保持排序。 */
  List<SchemeItemDO> selectSchemeItemsBySchemeIds(@Param("ids") List<String> ids);

  /** 仅加载本次修改和删除的成员，方案条件同时限制明细归属。 */
  List<SchemeItemDO> selectSchemeItemsBySchemeAndIds(
      @Param("schemeId") String schemeId, @Param("ids") List<String> ids);

  /** 只对本次新增SKU检查已有成员，避免读取全量快照。 */
  List<String> selectExistingSkuIdsByScheme(
      @Param("schemeId") String schemeId, @Param("skuIds") List<String> skuIds);

  /** 聚合不参与本次金额变化的成员，宽整数合计由业务层验证。 */
  com.mujian.backend.scheme.projection.SchemeAmountProjection selectSchemeAmountsExcludingIds(
      @Param("schemeId") String schemeId, @Param("excludedIds") List<String> excludedIds);

  /** 保存已验证的方案成员。 */
  void insertSchemeItem(@Param("row") SchemeItemDO schemeItemDO);

  /** 仅修改本方案指定成员的可编辑值，不刷新快照。 */
  int updateSchemeItemBySchemeAndId(@Param("row") SchemeItemDO schemeItemDO);

  /** 仅删除本方案指定成员。 */
  int deleteSchemeItemBySchemeAndId(@Param("schemeId") String schemeId, @Param("id") String id);

  /** 读取方案最近一次签发的链接。 */
  SchemeShareDO selectSchemeShareBySchemeId(@Param("id") String id);

  /** 通过精确token读取分享配置。 */
  SchemeShareDO selectSchemeShareByToken(@Param("token") String token);

  /** 创建独立分享链接。 */
  void insertSchemeShare(@Param("row") SchemeShareDO schemeShareDO);

  /** 兼容旧关闭入口，撤销本方案已签发链接，不影响快照。 */
  int disableSchemeSharesBySchemeId(
      @Param("id") String id, @Param("now") java.time.LocalDateTime now);

  /** 按签发请求找回原链接。 */
  SchemeShareDO selectSchemeShareByRequest(
      @Param("actor") String actor, @Param("requestId") String requestId);

  /** 按内容版本和价格模式读取唯一快照。 */
  SchemeShareSnapshotDO selectShareSnapshotByContent(
      @Param("schemeId") String schemeId,
      @Param("version") int version,
      @Param("hidePrice") boolean hidePrice);

  /** 匿名链接仅能读取其绑定快照。 */
  SchemeShareSnapshotDO selectShareSnapshotById(@Param("id") String id);

  /** 插入不可变快照，组合唯一约束防止并发重复。 */
  void insertShareSnapshot(@Param("row") SchemeShareSnapshotDO schemeShareSnapshotDO);

  /** 只对当前有效token累计成功浏览，饱和不溢出。 */
  void incrementSchemeShareViewsByToken(@Param("id") String id, @Param("token") String token);
}
