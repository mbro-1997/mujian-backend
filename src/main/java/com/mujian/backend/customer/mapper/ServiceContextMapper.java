package com.mujian.backend.customer.mapper;

import org.apache.ibatis.annotations.Param;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.customer.vo.ServiceViews.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceContextMapper {
  /** 判断该账号是否有真实入驻申请。 */
  boolean hasApplication(@Param("user") String user);

  /** 找出精确来源最近的本人上下文，批准后可定位本人的申请人历史。 */
  String latestContext(@Param("user") String user, @Param("role") String role, @Param("source") Source source);

  /** 来源进度包括独立人工登记的案件。 */
  Progress sourceProgress(@Param("user") String user, @Param("source") Source source);

  /** 回读已经关联该卡并属于本人的正式案件。 */
  Progress linkedProgress(@Param("id") String id, @Param("user") String user);

  /** 只计算当前身份有权咨询的对象。 */
  long countSources(@Param("user") String user, @Param("factory") String factory, @Param("type") String type, @Param("id") String id);

  /** 多来源全局分页或读取指定的已授权对象。 */
  List<SourceRow> selectSources(@Param("user") String user,
      @Param("factory") String factory,
      @Param("type") String type,
      @Param("id") String id,
      @Param("page") PageQuery page);

  /** 根据请求键恢复原卡片。 */
  ContextRow selectByRequest(@Param("key") String key);

  /** 单条上下文由服务层再次核对归属。 */
  ContextRow selectContext(@Param("id") String id, @Param("lock") boolean lock);

  /** 创建令牌摘要和来源快照。 */
  void insertContext(@Param("id") String id,
      @Param("key") String key,
      @Param("user") String user,
      @Param("role") String role,
      @Param("source") SourceRow source,
      @Param("hash") String hash,
      @Param("at") LocalDateTime at,
      @Param("expires") LocalDateTime expires);
}
