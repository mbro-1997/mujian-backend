package com.mujian.backend.student.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.service.VerificationCasework;
import com.mujian.backend.common.support.*;
import com.mujian.backend.student.domain.StudentVerificationDO;
import com.mujian.backend.student.mapper.StudentMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StudentExpiryService {
  private final LoginMapper loginMapper;
  private final StudentMapper studentMapper;
  private final VerificationCasework verificationCasework;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public StudentExpiryService(
      LoginMapper loginMapper,
      StudentMapper studentMapper,
      VerificationCasework verificationCasework,
      Clock clock,
      PlatformTransactionManager platformTransactionManager) {
    this.loginMapper = loginMapper;
    this.studentMapper = studentMapper;
    this.verificationCasework = verificationCasework;
    this.clock = clock;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transactionTemplate.setTimeout(5);
  }

  /** 执行既有后台补偿任务并保持失败隔离。 */
  @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Shanghai")
  public void scheduled() {
    expireBatch();
  }

  /** 按批次处理到期学生认证，复用单用户锁规则。 */
  public int expireBatch() {
    int changed = 0;
    for (com.mujian.backend.student.projection.StudentExpiryProjection studentExpiryProjection :
        studentMapper.selectDueStudentVerificationCandidates(now(clock))) {
      changed +=
          transactionTemplate.execute(
              t -> {
                loginMapper.selectUserByIdForUpdate(studentExpiryProjection.getUserId());
                return expireLocked(studentExpiryProjection.getId());
              });
    }
    return changed;
  }

  /** Caller already owns the shared user root lock; state and audit remain in its transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void expireOwnedLocked(String user) {
    for (String id : studentMapper.selectDueStudentVerificationIdsByOwner(user, now(clock)))
      expireLocked(id);
  }

  /** 再次核对到期状态后更新认证并保留并发保护。 */
  private int expireLocked(String id) {
    StudentVerificationDO studentVerificationDO =
        studentMapper.selectStudentVerificationByIdForUpdate(id);
    LocalDateTime at = now(clock);
    if (studentVerificationDO == null
        || !CODE_APPROVED.equals(studentVerificationDO.getVerificationStatus())
        || studentVerificationDO.getExpireAt().isAfter(at)) return 0;
    int count =
        studentMapper.expireStudentVerificationByVersion(
            id, studentVerificationDO.getVersion(), at);
    if (count == 1)
      verificationCasework.audit(
          "student",
          id,
          SystemIdentity.SYSTEM_USER_ID,
          "student_expire",
          null,
          Ids.next(),
          null,
          null,
          null,
          null,
          view(
              "schemaVersion",
              1,
              "status",
              CODE_APPROVED,
              "version",
              studentVerificationDO.getVersion()),
          view(
              "schemaVersion",
              1,
              "status",
              CODE_EXPIRED,
              "version",
              studentVerificationDO.getVersion() + 1),
          at);
    return count;
  }
}
