package com.mujian.backend.student.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.service.VerificationCasework;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.support.*;
import com.mujian.backend.material.service.*;
import com.mujian.backend.realname.vo.RealnameApplicationVO;
import com.mujian.backend.student.domain.StudentVerificationDO;
import com.mujian.backend.student.mapper.StudentMapper;
import com.mujian.backend.student.policy.StudentValidityPolicy;
import com.mujian.backend.student.projection.StudentListProjection;
import com.mujian.backend.student.service.*;
import com.mujian.backend.student.vo.*;
import com.mujian.backend.verification.dto.VerificationListQueryDTO;
import java.time.*;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class StudentQueryServiceImpl implements StudentQueryService {
  private final StudentMapper studentMapper;
  private final LoginMapper loginMapper;
  private final VerificationAccess verificationAccess;
  private final VerificationCasework verificationCasework;
  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final com.mujian.backend.realname.service.RealnameQueryService realnameQueryService;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public StudentQueryServiceImpl(
      StudentMapper studentMapper,
      LoginMapper loginMapper,
      VerificationAccess verificationAccess,
      VerificationCasework verificationCasework,
      Clock clock,
      ObjectMapper objectMapper,
      com.mujian.backend.realname.service.RealnameQueryService realnameQueryService) {
    this.studentMapper = studentMapper;
    this.loginMapper = loginMapper;
    this.verificationAccess = verificationAccess;
    this.verificationCasework = verificationCasework;
    this.clock = clock;
    this.objectMapper = objectMapper;
    this.realnameQueryService = realnameQueryService;
  }

  /** 按既有字段和脱敏规则组装认证申请响应。 */
  public StudentApplicationVO application(StudentVerificationDO studentVerificationDO) {
    if (studentVerificationDO == null) return null;
    String status =
        StudentValidityPolicy.status(
            studentVerificationDO.getVerificationStatus(),
            studentVerificationDO.getExpireAt(),
            now(clock));
    return new StudentApplicationVO()
        .id(studentVerificationDO.getId())
        .applicationNo(studentVerificationDO.getApplicationNo())
        .status(status)
        .schoolName(studentVerificationDO.getSchoolName())
        .materialCount(objectMapper.readTree(studentVerificationDO.getStudentCardUrls()).size())
        .submittedAt(time(studentVerificationDO.getSubmittedAt()))
        .reviewedAt(time(studentVerificationDO.getReviewedAt()))
        .expireAt(time(studentVerificationDO.getExpireAt()))
        .isCurrentlyValid(CODE_APPROVED.equals(status))
        .rejectReason(studentVerificationDO.getRejectReason());
  }

  /** 读取当前用户的有效认证记录。 */
  public StudentVerificationDO currentRow(String user) {
    return studentMapper.selectCurrentStudentVerification(user, now(clock));
  }

  /** 组装当前认证申请，保持空申请语义。 */
  public StudentApplicationVO currentApplication(String user) {
    return application(currentRow(user));
  }

  /** 读取当前生效记录并组装业务上下文。 */
  public StudentCurrentVO current(String user) {
    boolean active = verificationAccess.owner(user, "student", false);
    StudentApplicationVO studentApplicationVO = currentApplication(user);
    String status =
        studentApplicationVO == null ? CODE_UNVERIFIED : studentApplicationVO.getStatus();
    String next =
        !active
            ? CODE_ACCOUNT_RESTRICTED
            : switch (status) {
              case CODE_UNVERIFIED -> CODE_FILL_FORM;
              case CODE_PENDING -> CODE_WAIT_REVIEW;
              case CODE_APPROVED -> CODE_VIEW_APPROVED;
              case CODE_REJECTED -> CODE_RESUBMIT;
              default -> CODE_RESUBMIT;
            };
    StudentCurrentVO studentCurrentVO =
        new StudentCurrentVO()
            .application(studentApplicationVO)
            .canSubmit(Set.of(CODE_FILL_FORM, CODE_RESUBMIT).contains(next))
            .nextAction(next)
            .consent(VerificationMaterials.consentView("student"))
            .uploadPolicy(VerificationMaterials.policy());
    studentCurrentVO.setRealnameStatus(realnameQueryService.currentStatus(user));
    return studentCurrentVO;
  }

  /** 按原请求编号恢复已提交结果并核对访问权限。 */
  public StudentApplicationVO recover(String user, String key) {
    verificationAccess.owner(user, "student", false);
    StudentVerificationDO studentVerificationDO =
        studentMapper.selectStudentVerificationByRequest(id(key));
    if (studentVerificationDO == null || !user.equals(studentVerificationDO.getUserId()))
      throw missing();
    return application(studentVerificationDO);
  }

  /** 检查审核权限后返回申请详情和工单信息。 */
  public StudentDetailVO detail(String actor, String id) {
    java.util.List<String> permissions = verificationAccess.readPermissions(actor, "admin:student:read");
    StudentVerificationDO studentVerificationDO =
        studentMapper.selectStudentVerificationById(id(id));
    if (studentVerificationDO == null) throw missing();
    AdminCaseDO adminCaseDO =
        verificationCasework.associated("student", studentVerificationDO, false);
    StudentDetailVO studentDetailVO =
        new StudentDetailVO()
            .application(application(studentVerificationDO))
            .userId(studentVerificationDO.getUserId())
            .nickname(loginMapper.selectUserNicknameById(studentVerificationDO.getUserId()))
            .phoneMasked(
                maskPhone(loginMapper.selectUserPhoneById(studentVerificationDO.getUserId())))
            .caseId(adminCaseDO.getId())
            .caseVersion(adminCaseDO.getVersion())
            .version(studentVerificationDO.getVersion())
            .reviewedByAdminUserId(studentVerificationDO.getReviewedByAdminUserId())
            .auditRemark(verificationCasework.remark(id))
            .canViewSensitive(permissions.contains("admin:student:sensitive"))
            .canReview(
                permissions.contains("admin:student:review")
                    && CODE_PENDING.equals(studentVerificationDO.getVerificationStatus())
                    && ownerActive(studentVerificationDO.getUserId()));
    RealnameApplicationVO realnameApplicationVO =
        realnameQueryService.currentApplication(studentVerificationDO.getUserId());
    studentDetailVO.setRealnameStatus(
        realnameApplicationVO == null ? CODE_UNVERIFIED : realnameApplicationVO.getStatus());
    studentDetailVO.setRealNameMasked(
        realnameApplicationVO == null ? null : realnameApplicationVO.getRealNameMasked());
    studentDetailVO.setIdCardMasked(
        realnameApplicationVO == null ? null : realnameApplicationVO.getIdCardMasked());
    studentDetailVO.setCanViewRealnameSensitive(
        permissions.contains("admin:realname:read")
            && permissions.contains("admin:realname:sensitive"));

    return studentDetailVO;
  }

  /** 确认申请所属账号处于允许审核的状态。 */
  private boolean ownerActive(String user) {
    try {
      return verificationAccess.owner(user, "student", false);
    } catch (ApiFailure apiFailure) {
      return false;
    }
  }

  /** 校验筛选后执行数据库分页，仅组装列表所需字段。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<StudentListItemVO> list(
      String actor, VerificationListQueryDTO verificationListQueryDTO) {
    verificationAccess.readPermissions(actor, "admin:student:read");
    int
        page =
            com.mujian.backend.common.api.PageQuery.parse(
                verificationListQueryDTO.getPage(), 1, Integer.MAX_VALUE),
        size =
            com.mujian.backend.common.api.PageQuery.parse(
                verificationListQueryDTO.getPageSize(), 20, 100);
    LocalDateTime
        from =
            verificationListQueryDTO.getSubmittedFrom() != null
                ? OffsetDateTime.parse(verificationListQueryDTO.getSubmittedFrom())
                    .toLocalDateTime()
                : null,
        to =
            verificationListQueryDTO.getSubmittedTo() != null
                ? OffsetDateTime.parse(verificationListQueryDTO.getSubmittedTo()).toLocalDateTime()
                : null;
    if (from != null && to != null && !from.isBefore(to)) throw ApiFailure.invalid();
    com.mujian.backend.verification.dto.VerificationFilter verificationFilter =
        com.mujian.backend.verification.dto.VerificationFilter.of(
            verificationListQueryDTO.getStatus() == null
                ? ""
                : verificationListQueryDTO.getStatus(),
            verificationListQueryDTO.getKeyword(),
            from,
            to,
            page,
            size,
            now(clock));
    long total = studentMapper.selectStudentVerificationCountByQuery(verificationFilter);
    List<StudentListProjection> rows =
        verificationFilter.offset() >= total
            ? List.of()
            : studentMapper.selectStudentVerificationPageByQuery(verificationFilter);
    List<StudentListItemVO> items = new ArrayList<>();
    for (StudentListProjection studentListProjection : rows) {
      items.add(
          new StudentListItemVO()
              .id(studentListProjection.getId())
              .applicationNo(studentListProjection.getApplicationNo())
              .userId(studentListProjection.getUserId())
              .nickname(studentListProjection.getNickname())
              .status(
                  StudentValidityPolicy.status(
                      studentListProjection.getVerificationStatus(),
                      studentListProjection.getExpireAt(),
                      now(clock)))
              .submittedAt(time(studentListProjection.getSubmittedAt()))
              .schoolName(studentListProjection.getSchoolName())
              .expireAt(time(studentListProjection.getExpireAt())));
    }
    return new PageResult<>(items, Long.toString(total), page, size);
  }
}
