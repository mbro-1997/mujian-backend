package com.mujian.backend.scheme.mapper;

import com.mujian.backend.scheme.domain.SchemeExportDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SchemeExportMapper {

  /** 读取本人导出状态。 */
  SchemeExportDO selectOwnedExportById(@Param("actor") String actor, @Param("id") String id);

  /** 读取指定导出供生成完成CAS。 */
  SchemeExportDO selectExportById(@Param("id") String id);

  /** 读取仍生成中的记录。 */
  SchemeExportDO selectProcessingExportBySchemeId(@Param("schemeId") String schemeId);

  /** 冷却以最近接纳时间计算。 */
  LocalDateTime selectLatestExportAttemptBySchemeId(@Param("schemeId") String schemeId);

  /** 上海自然日内所有接纳尝试均计数。 */
  long selectExportCountByActorAndDay(
      @Param("actor") String actor,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /** 与回执同事务预留一次真实生成。 */
  void insertExport(@Param("row") SchemeExportDO schemeExportDO);

  /** 未过deadline才允许发布成功。 */
  int completeExport(
      @Param("id") String id,
      @Param("now") LocalDateTime now,
      @Param("expires") LocalDateTime expires,
      @Param("key") String key);

  /** 超时或生成失败释放生成守卫，不退配额。 */
  int failExport(
      @Param("id") String id, @Param("now") LocalDateTime now, @Param("reason") String reason);

  /** 有限批量寻找超时生成。 */
  List<SchemeExportDO> selectTimedOutExportsByDeadline(@Param("now") LocalDateTime now);

  /** 有限批量清理终态元数据，回执永久保留。 */
  int deleteExpiredExportRecordsByTime(@Param("now") LocalDateTime now);
}
