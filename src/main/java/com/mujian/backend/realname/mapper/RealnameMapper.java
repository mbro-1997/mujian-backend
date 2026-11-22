package com.mujian.backend.realname.mapper;

import com.mujian.backend.realname.domain.RealnameVerificationDO;
import com.mujian.backend.verification.dto.VerificationFilter;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RealnameMapper {
  /** 按申请编号读取实名认证记录。 */
  RealnameVerificationDO selectRealnameVerificationById(@Param("id") String id);

  /** 锁定申请并读取实名认证记录。 */
  RealnameVerificationDO selectRealnameVerificationByIdForUpdate(@Param("id") String id);

  /** 按用户及请求编号恢复实名认证记录。 */
  RealnameVerificationDO selectRealnameVerificationByRequest(@Param("key") String key);

  /** 读取用户当前实名认证记录。 */
  RealnameVerificationDO selectCurrentRealnameVerification(
      @Param("user") String user, @Param("now") LocalDateTime now);

  /** 只读取实名准入状态，不取回证件密文。 */
  String selectCurrentRealnameStatus(@Param("user") String user);

  /** 按版本更新实名审核决定及必要时间。 */
  int reviewRealnameVerificationByVersion(
      @Param("id") String id,
      @Param("version") int version,
      @Param("status") String status,
      @Param("actor") String actor,
      @Param("remark") String remark,
      @Param("now") LocalDateTime now,
      @Param("expiry") LocalDateTime expiry);

  /** 保存新的实名申请，依赖原唯一约束防重。 */
  void insertRealnameVerification(@Param("realnameVerificationDO") RealnameVerificationDO realnameVerificationDO);

  /** 按相同筛选统计实名审核列表总数。 */
  long selectRealnameVerificationCountByQuery(@Param("verificationFilter") VerificationFilter verificationFilter);

  /** 按稳定顺序分页读取实名列表必要字段。 */
  List<com.mujian.backend.realname.projection.RealnameListProjection>
      selectRealnameVerificationPageByQuery(@Param("verificationFilter") VerificationFilter verificationFilter);
}
