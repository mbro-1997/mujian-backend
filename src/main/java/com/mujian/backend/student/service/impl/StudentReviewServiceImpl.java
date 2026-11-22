package com.mujian.backend.student.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.service.VerificationCasework;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.*;
import com.mujian.backend.material.service.*;
import com.mujian.backend.reward.service.FirstRewardCaptureService;
import com.mujian.backend.student.domain.StudentVerificationDO;
import com.mujian.backend.student.mapper.StudentMapper;
import com.mujian.backend.student.policy.StudentValidityPolicy;
import com.mujian.backend.student.service.*;
import com.mujian.backend.student.vo.StudentReviewResultVO;
import com.mujian.backend.verification.dto.VerificationReviewRequestDTO;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class StudentReviewServiceImpl implements StudentReviewService {

  private final StudentMapper studentMapper;
  private final LoginMapper loginMapper;
  private final VerificationAccess verificationAccess;
  private final VerificationCasework verificationCasework;
  private final FirstRewardCaptureService firstRewardCaptureService;
  private final Clock clock;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public StudentReviewServiceImpl(
      StudentMapper studentMapper,
      LoginMapper loginMapper,
      VerificationAccess verificationAccess,
      VerificationCasework verificationCasework,
      FirstRewardCaptureService firstRewardCaptureService,
      Clock clock) {
    this.studentMapper = studentMapper;
    this.loginMapper = loginMapper;
    this.verificationAccess = verificationAccess;
    this.verificationCasework = verificationCasework;
    this.firstRewardCaptureService = firstRewardCaptureService;
    this.clock = clock;
  }

  /** 按幂等和固定锁序审核申请，原子保存结果及审计。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public StudentReviewResultVO review(
      String actor,
      String application,
      String request,
      boolean approve,
      ReviewCommand reviewCommand,
      String trace) {
    id(application);
    id(request);
    id(reviewCommand.caseId());
    verificationAccess.admin(actor, "admin:student:review");
    StudentVerificationDO studentVerificationDOInitial =
        studentMapper.selectStudentVerificationById(application);
    if (studentVerificationDOInitial == null) throw missing();
    TreeSet<String> roots =
        new TreeSet<String>(List.of(actor, studentVerificationDOInitial.getUserId()));
    for (String root : roots) loginMapper.selectUserByIdForUpdate(root);
    verificationAccess.admin(actor, "admin:student:review");
    StudentVerificationDO studentVerificationDOLocked =
        studentMapper.selectStudentVerificationByIdForUpdate(application);
    AdminCaseDO adminCaseDO =
        verificationCasework.associated("student", studentVerificationDOLocked, true);
    String remark = reviewCommand.remark() == null ? null : reviewCommand.remark().trim();
    if (remark != null && remark.isEmpty()) remark = null;
    if (!approve && remark == null
        || remark != null && remark.codePointCount(0, remark.length()) > 255)
      throw ApiFailure.invalid();
    String key = "student-review:" + request;
    VerificationReviewRequestDTO verificationReviewRequestDTO = new VerificationReviewRequestDTO();
    verificationReviewRequestDTO.setSchemaVersion(1);
    verificationReviewRequestDTO.setApplicationId(application);
    verificationReviewRequestDTO.setCaseId(reviewCommand.caseId());
    verificationReviewRequestDTO.setDecision(approve ? "approve" : "reject");
    verificationReviewRequestDTO.setExpectedVersion(reviewCommand.expectedVersion());
    verificationReviewRequestDTO.setExpectedCaseVersion(reviewCommand.expectedCaseVersion());
    verificationReviewRequestDTO.setRemark(remark);

    if (approve) {
      if (reviewCommand.validYears() == null
          || reviewCommand.validYears() < 1
          || reviewCommand.validYears() > 5) throw ApiFailure.invalid();
      verificationReviewRequestDTO.setValidYears(reviewCommand.validYears());
    } else if (reviewCommand.validYears() != null) throw ApiFailure.invalid();
    StudentReviewResultVO studentReviewResultVOReplay =
        verificationCasework.replay(
            key, actor, application, verificationReviewRequestDTO, StudentReviewResultVO.class);
    if (studentReviewResultVOReplay != null) return studentReviewResultVOReplay;
    verificationAccess.owner(studentVerificationDOLocked.getUserId(), "student", true);
    if (!CODE_PENDING.equals(studentVerificationDOLocked.getVerificationStatus()))
      throw conflict(CODE_STUDENT_ALREADY_REVIEWED);
    if (!adminCaseDO.getId().equals(reviewCommand.caseId())
        || studentVerificationDOLocked.getVersion() != reviewCommand.expectedVersion()
        || adminCaseDO.getVersion() != reviewCommand.expectedCaseVersion())
      throw conflict(CODE_VERSION_CONFLICT);
    LocalDateTime now = now(clock);
    LocalDateTime expiry =
        approve ? StudentValidityPolicy.expiry(now, reviewCommand.validYears()) : null;
    String status = approve ? CODE_APPROVED : CODE_REJECTED;
    if (studentMapper.reviewStudentVerificationByVersion(
            application,
            studentVerificationDOLocked.getVersion(),
            status,
            actor,
            remark,
            now,
            expiry)
        != 1) throw conflict(CODE_VERSION_CONFLICT);
    StudentReviewResultVO studentReviewResultVONew = new StudentReviewResultVO();
    studentReviewResultVONew.setActionId(Ids.next());
    studentReviewResultVONew.setApplicationId(application);
    studentReviewResultVONew.setStatus(status);
    studentReviewResultVONew.setVersion(studentVerificationDOLocked.getVersion() + 1);
    studentReviewResultVONew.setCaseId(adminCaseDO.getId());
    studentReviewResultVONew.setCaseVersion(adminCaseDO.getVersion() + 1);
    studentReviewResultVONew.setReviewedAt(time(now));
    studentReviewResultVONew.setReviewedByAdminUserId(actor);

    studentReviewResultVONew.setExpireAt(time(expiry));
    verificationCasework.finish(
        "student",
        studentVerificationDOLocked,
        adminCaseDO,
        actor,
        key,
        verificationReviewRequestDTO,
        studentReviewResultVONew,
        trace,
        now);
    return studentReviewResultVONew;
  }
}
