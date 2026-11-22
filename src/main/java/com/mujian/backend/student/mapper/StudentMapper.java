package com.mujian.backend.student.mapper;

import com.mujian.backend.student.domain.StudentVerificationDO;
import com.mujian.backend.verification.dto.VerificationFilter;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface StudentMapper {
  /** 按申请编号读取学生认证记录。 */
  StudentVerificationDO selectStudentVerificationById(@Param("id") String id);

  /** 锁定申请并读取学生认证记录。 */
  StudentVerificationDO selectStudentVerificationByIdForUpdate(@Param("id") String id);

  /** 按用户及请求编号恢复学生认证记录。 */
  StudentVerificationDO selectStudentVerificationByRequest(@Param("key") String key);

  /** 读取用户当前学生认证记录。 */
  StudentVerificationDO selectCurrentStudentVerification(
      @Param("user") String user, @Param("now") LocalDateTime now);

  /** 按批次读取已到期学生认证候选。 */
  List<com.mujian.backend.student.projection.StudentExpiryProjection>
      selectDueStudentVerificationCandidates(@Param("now") LocalDateTime now);

  /** 读取指定用户需到期处理的申请编号。 */
  List<String> selectDueStudentVerificationIdsByOwner(
      @Param("user") String user, @Param("now") LocalDateTime now);

  /** 按版本和有效期更新已到期认证。 */
  int expireStudentVerificationByVersion(
      @Param("id") String id, @Param("version") int version, @Param("now") LocalDateTime now);

  /** 按版本更新学生审核决定及必要时间。 */
  int reviewStudentVerificationByVersion(
      @Param("id") String id,
      @Param("version") int version,
      @Param("status") String status,
      @Param("actor") String actor,
      @Param("remark") String remark,
      @Param("now") LocalDateTime now,
      @Param("expiry") LocalDateTime expiry);

  /** 保存新的学生申请，依赖原唯一约束防重。 */
  void insertStudentVerification(@Param("studentVerificationDO") StudentVerificationDO studentVerificationDO);

  /** 按相同筛选统计学生审核列表总数。 */
  long selectStudentVerificationCountByQuery(@Param("verificationFilter") VerificationFilter verificationFilter);

  /** 按稳定顺序分页读取学生列表必要字段。 */
  List<com.mujian.backend.student.projection.StudentListProjection>
      selectStudentVerificationPageByQuery(@Param("verificationFilter") VerificationFilter verificationFilter);
}
