package com.mujian.backend.prop.utils;

import com.mujian.backend.prop.domain.PropInstanceStatusLogDO;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.domain.PropSnInstanceDO;
import com.mujian.backend.prop.mapper.PropInstanceStatusLogMapper;
import com.mujian.backend.prop.mapper.PropQrCodeMapper;
import com.mujian.backend.prop.mapper.PropSnInstanceMapper;
import java.util.*;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.*;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Uses the Spring-managed transaction connection, preserving JDBC batch insertion order. */
@Component
public final class PropBatchWriter {
  private final SqlSessionFactory sqlSessionFactory;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropBatchWriter(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  /** 在原事务内批量插入业务二维码。 */
  public void insertQrCodes(List<PropQrCodeDO> propQrCodeDOList) {
    insert(propQrCodeDOList, PropQrCodeMapper.class, PropQrCodeMapper::insertPropQrCode);
  }

  /** 在原事务内批量插入实物资产。 */
  public void insertInstances(List<PropSnInstanceDO> propSnInstanceDOList) {
    insert(
        propSnInstanceDOList,
        PropSnInstanceMapper.class,
        PropSnInstanceMapper::insertPropSnInstance);
  }

  /** 在原事务内批量插入资产状态流水。 */
  public void insertStatusLogs(List<PropInstanceStatusLogDO> propInstanceStatusLogDOList) {
    insert(
        propInstanceStatusLogDOList,
        PropInstanceStatusLogMapper.class,
        PropInstanceStatusLogMapper::insertPropInstanceStatusLog);
  }

  /** 在现有Spring事务连接内按顺序提交明确类型的批量写入。 */
  private <M, T> void insert(
      List<T> rows, Class<M> mapperType, java.util.function.BiConsumer<M, T> biConsumer) {
    if (rows.isEmpty()) return;
    try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
      M mapper = sqlSession.getMapper(mapperType);
      for (T row : rows) biConsumer.accept(mapper, row);
      sqlSession.flushStatements();
      sqlSession.commit();
    } catch (PersistenceException persistenceException) {
      DataAccessException dataAccessException =
          new MyBatisExceptionTranslator(
                  sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(), true)
              .translateExceptionIfPossible(persistenceException);
      if (dataAccessException != null) throw dataAccessException;
      throw persistenceException;
    }
  }
}
