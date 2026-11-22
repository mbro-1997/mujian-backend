package com.mujian.backend.factory.mapper;

import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.FactoryPageQueryDTO;
import com.mujian.backend.factory.projection.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FactoryProfileMapper {

  /** 按账号归属读取评价所需的工厂 ID。 */
  String selectFactoryIdByOwner(@Param("owner") String owner);

  /** 按所有者查询工厂资料，可加行锁。 */
  FactorySourceProjection selectFactoryProfileByOwner(
      @Param("owner") String owner, @Param("lock") boolean lock);

  /** 按筛选条件统计工厂数量。 */
  long selectFactoryProfileCountByQuery(@Param("factoryPageQueryDTO") FactoryPageQueryDTO factoryPageQueryDTO);

  /** 按筛选条件分页读取工厂资料。 */
  List<FactorySourceProjection> selectFactoryProfilePageByQuery(@Param("factoryPageQueryDTO") FactoryPageQueryDTO factoryPageQueryDTO);

  /** 保存入驻审核通过后的正式工厂资料。 */
  void insertFactoryProfile(@Param("factoryProfileDO") FactoryProfileDO factoryProfileDO);

  /** 更新审核生效后的工厂资料和版本。 */
  int updateFactoryProfileMaterials(@Param("factoryProfileDO") FactoryProfileDO factoryProfileDO);
}
