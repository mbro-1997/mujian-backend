package com.mujian.backend.factory.mapper;

import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.FactoryPageQueryDTO;
import com.mujian.backend.factory.projection.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FactoryReviewMapper {

  /** 按来源和编号读取工厂资料或审核申请，可加行锁。 */
  FactorySourceProjection selectFactorySourceById(
      @Param("source") FactorySource source, @Param("id") String id, @Param("lock") boolean lock);

  /** 读取申请人或工厂的最新审核申请。 */
  FactorySourceProjection selectCurrentFactoryAuditByOwner(
      @Param("source") FactorySource source, @Param("owner") String owner);

  /** 按归属和请求编号恢复原审核申请。 */
  FactorySourceProjection selectFactoryAuditByRequest(
      @Param("source") FactorySource source,
      @Param("owner") String owner,
      @Param("key") String key);

  /** 查找缺少审核工单的待审申请，供补偿任务处理。 */
  List<FactorySourceProjection> selectFactorySourcesMissingCase(
      @Param("source") FactorySource source);

  /** 按来源和筛选条件统计工厂审核申请数量。 */
  long selectFactoryReviewCountByQuery(
      @Param("factoryPageQueryDTO") FactoryPageQueryDTO factoryPageQueryDTO, @Param("source") FactorySource source);

  /** 按来源和筛选条件分页读取工厂审核队列。 */
  List<FactorySourceProjection> selectFactoryReviewPageByQuery(
      @Param("factoryPageQueryDTO") FactoryPageQueryDTO factoryPageQueryDTO, @Param("source") FactorySource source);
}
