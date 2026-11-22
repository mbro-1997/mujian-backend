package com.mujian.backend.student.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.service.VerificationCasework;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.*;
import com.mujian.backend.infrastructure.crypto.VerificationCrypto;
import com.mujian.backend.material.service.*;
import com.mujian.backend.material.service.VerificationMaterials.Receipt;
import com.mujian.backend.student.domain.StudentVerificationDO;
import com.mujian.backend.student.mapper.StudentMapper;
import com.mujian.backend.student.service.*;
import com.mujian.backend.student.vo.StudentApplicationVO;
import com.mujian.backend.verification.vo.VerificationSubmitVO;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class StudentSubmissionServiceImpl implements StudentSubmissionService {

  private final StudentExpiryService studentExpiryService;
  private final StudentMapper studentMapper;
  private final LoginMapper loginMapper;
  private final VerificationAccess verificationAccess;
  private final VerificationMaterials verificationMaterials;
  private final VerificationCrypto verificationCrypto;
  private final VerificationCasework verificationCasework;
  private final StudentQueryService studentQueryService;
  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public StudentSubmissionServiceImpl(
      StudentExpiryService studentExpiryService,
      StudentMapper studentMapper,
      LoginMapper loginMapper,
      VerificationAccess verificationAccess,
      VerificationMaterials verificationMaterials,
      VerificationCrypto verificationCrypto,
      VerificationCasework verificationCasework,
      StudentQueryService studentQueryService,
      Clock clock,
      ObjectMapper objectMapper,
      PlatformTransactionManager platformTransactionManager) {
    this.studentExpiryService = studentExpiryService;
    this.studentMapper = studentMapper;
    this.loginMapper = loginMapper;
    this.verificationAccess = verificationAccess;
    this.verificationMaterials = verificationMaterials;
    this.verificationCrypto = verificationCrypto;
    this.verificationCasework = verificationCasework;
    this.studentQueryService = studentQueryService;
    this.clock = clock;
    this.objectMapper = objectMapper;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    transactionTemplate.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 核对重试材料与原申请一致后返回原结果。 */
  private VerificationSubmitVO<StudentApplicationVO> replay(
      String user,
      StudentVerificationDO studentVerificationDOOld,
      String name,
      List<String> keys,
      String version) {
    if (!user.equals(studentVerificationDOOld.getUserId())) throw missing();
    if (!version.equals(studentVerificationDOOld.getMaterialConsentVersion())
        || !name.equals(studentVerificationDOOld.getSchoolName())
        || !objectMapper
            .valueToTree(keys)
            .equals(objectMapper.readTree(studentVerificationDOOld.getStudentCardUrls())))
      throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
    return new VerificationSubmitVO<>(
        CODE_REPLAYED, studentQueryService.application(studentVerificationDOOld));
  }

  /** 核对幂等与待审状态，在原事务内保存认证申请。 */
  public VerificationSubmitVO<StudentApplicationVO> submit(
      String user, String key, SubmitRequest submitRequest) {
    id(key);
    verificationAccess.owner(user, "student", false);
    String name = text(submitRequest.schoolName(), 128);
    List<String> uploads = new ArrayList<>();
    for (String uploadId : submitRequest.materialUploadIds())
      uploads.add(com.mujian.backend.common.support.D2Values.id(uploadId));
    if (uploads.size() < 2 || uploads.size() > 3 || new HashSet<>(uploads).size() != uploads.size())
      throw ApiFailure.invalid();
    List<String> keys = new ArrayList<>();
    for (String uploadId : uploads) keys.add(VerificationMaterials.key(user, "student", uploadId));
    StudentVerificationDO studentVerificationDOOld =
        studentMapper.selectStudentVerificationByRequest(key);
    if (studentVerificationDOOld != null)
      return replay(user, studentVerificationDOOld, name, keys, submitRequest.consentVersion());
    verificationAccess.owner(user, "student", true);
    VerificationMaterials.consent(
        "student", submitRequest.consentVersion(), submitRequest.consentAccepted());
    VerificationSubmitVO<StudentApplicationVO> verificationSubmitVO =
        transactionTemplate.execute(
            s -> {
              loginMapper.selectUserByIdForUpdate(user);
              verificationAccess.owner(user, "student", false);
              StudentVerificationDO studentVerificationDOCommitted =
                  studentMapper.selectStudentVerificationByRequest(key);
              if (studentVerificationDOCommitted != null)
                return replay(
                    user,
                    studentVerificationDOCommitted,
                    name,
                    keys,
                    submitRequest.consentVersion());
              verificationAccess.owner(user, "student", true);
              StudentVerificationDO studentVerificationDOCurrent =
                  studentMapper.selectCurrentStudentVerification(user, now(clock));
              if (studentVerificationDOCurrent != null) {
                String status =
                    studentQueryService.application(studentVerificationDOCurrent).getStatus();
                if (status.equals(CODE_PENDING) || status.equals(CODE_APPROVED))
                  return new VerificationSubmitVO<>(
                      status.equals(CODE_PENDING) ? CODE_EXISTING_PENDING : CODE_ALREADY_APPROVED,
                      studentQueryService.application(studentVerificationDOCurrent));
              }
              return null;
            });
    if (verificationSubmitVO != null) return verificationSubmitVO;
    List<VerificationMaterials.Receipt> receipts = new ArrayList<>();
    try {
      for (int i = 0; i < uploads.size(); i++)
        receipts.add(
            verificationMaterials.receipt(
                user, "student_card", submitRequest.consentVersion(), uploads.get(i)));
    } catch (ApiFailure apiFailure) {
      StudentVerificationDO committed = studentMapper.selectStudentVerificationByRequest(key);
      if (committed != null)
        return replay(user, committed, name, keys, submitRequest.consentVersion());
      throw apiFailure;
    }
    try {
      return transactionTemplate.execute(
          s -> {
            loginMapper.selectUserByIdForUpdate(user);
            verificationAccess.owner(user, "student", false);
            StudentVerificationDO committed = studentMapper.selectStudentVerificationByRequest(key);
            if (committed != null)
              return replay(user, committed, name, keys, submitRequest.consentVersion());
            verificationAccess.owner(user, "student", true);
            StudentVerificationDO current =
                studentMapper.selectCurrentStudentVerification(user, now(clock));
            if (current != null) {
              String status = studentQueryService.application(current).getStatus();
              if (status.equals(CODE_PENDING) || status.equals(CODE_APPROVED))
                return new VerificationSubmitVO<>(
                    status.equals(CODE_PENDING) ? CODE_EXISTING_PENDING : CODE_ALREADY_APPROVED,
                    studentQueryService.application(current));
            }
            for (Receipt receipt : receipts) verificationMaterials.validateNewReference(receipt);
            LocalDateTime now = now(clock);
            studentExpiryService.expireOwnedLocked(user);
            String application = Ids.next();
            Instant acceptedAt = null;
            for (Receipt receipt : receipts) {
              if (acceptedAt == null || receipt.acceptedAt().isBefore(acceptedAt))
                acceptedAt = receipt.acceptedAt();
            }
            if (acceptedAt == null) throw new NoSuchElementException();
            StudentVerificationDO studentVerificationDO = new StudentVerificationDO();
            studentVerificationDO.setId(application);
            studentVerificationDO.setUserId(user);
            studentVerificationDO.setSubmitRequestId(key);
            studentVerificationDO.setSubmittedAt(now);
            studentVerificationDO.setMaterialConsentVersion(submitRequest.consentVersion());
            studentVerificationDO.setMaterialConsentAt(LocalDateTime.ofInstant(acceptedAt, ZONE));
            studentVerificationDO.setVerificationStatus(CODE_PENDING);
            studentVerificationDO.setCreatedAt(now);
            studentVerificationDO.setUpdatedAt(now);

            studentVerificationDO.setApplicationNo("SV" + application);
            studentVerificationDO.setSchoolName(name);
            studentVerificationDO.setStudentCardUrls(objectMapper.writeValueAsString(keys));

            studentMapper.insertStudentVerification(studentVerificationDO);
            verificationCasework.create("student", user, application, now);
            return new VerificationSubmitVO<>(
                CODE_CREATED,
                studentQueryService.application(
                    studentMapper.selectStudentVerificationById(application)));
          });
    } catch (org.springframework.dao.DuplicateKeyException duplicateKeyException) {
      StudentVerificationDO committed = studentMapper.selectStudentVerificationByRequest(key);
      if (committed != null)
        return replay(user, committed, name, keys, submitRequest.consentVersion());
      throw duplicateKeyException;
    }
  }
}
