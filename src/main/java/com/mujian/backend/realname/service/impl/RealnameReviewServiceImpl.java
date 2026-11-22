package com.mujian.backend.realname.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.service.VerificationCasework;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.*;
import com.mujian.backend.material.service.*;
import com.mujian.backend.realname.domain.RealnameVerificationDO;
import com.mujian.backend.realname.mapper.RealnameMapper;
import com.mujian.backend.realname.service.*;
import com.mujian.backend.reward.service.FirstRewardCaptureService;
import com.mujian.backend.verification.dto.VerificationReviewRequestDTO;
import com.mujian.backend.verification.vo.VerificationReviewResultVO;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RealnameReviewServiceImpl implements RealnameReviewService {

  private final RealnameMapper realnameMapper;
  private final LoginMapper loginMapper;
  private final VerificationAccess verificationAccess;
  private final VerificationCasework verificationCasework;
  private final FirstRewardCaptureService firstRewardCaptureService;
  private final Clock clock;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public RealnameReviewServiceImpl(
      RealnameMapper realnameMapper,
      LoginMapper loginMapper,
      VerificationAccess verificationAccess,
      VerificationCasework verificationCasework,
      FirstRewardCaptureService firstRewardCaptureService,
      Clock clock) {
    this.realnameMapper = realnameMapper;
    this.loginMapper = loginMapper;
    this.verificationAccess = verificationAccess;
    this.verificationCasework = verificationCasework;
    this.firstRewardCaptureService = firstRewardCaptureService;
    this.clock = clock;
  }

  /** 按幂等和固定锁序审核申请，原子保存结果及审计。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public VerificationReviewResultVO review(
      String actor,
      String application,
      String request,
      boolean approve,
      ReviewCommand reviewCommand,
      String trace) {
    id(application);
    id(request);
    id(reviewCommand.caseId());
    verificationAccess.admin(actor, "admin:realname:review");
    RealnameVerificationDO realnameVerificationDOInitial =
        realnameMapper.selectRealnameVerificationById(application);
    if (realnameVerificationDOInitial == null) throw missing();
    TreeSet<String> roots =
        new TreeSet<String>(List.of(actor, realnameVerificationDOInitial.getUserId()));
    for (String root : roots) loginMapper.selectUserByIdForUpdate(root);
    verificationAccess.admin(actor, "admin:realname:review");
    RealnameVerificationDO realnameVerificationDOLocked =
        realnameMapper.selectRealnameVerificationByIdForUpdate(application);
    AdminCaseDO adminCaseDO =
        verificationCasework.associated("realname", realnameVerificationDOLocked, true);
    String remark = reviewCommand.remark() == null ? null : reviewCommand.remark().trim();
    if (remark != null && remark.isEmpty()) remark = null;
    if (!approve && remark == null
        || remark != null && remark.codePointCount(0, remark.length()) > 255)
      throw ApiFailure.invalid();
    String key = "realname-review:" + request;
    VerificationReviewRequestDTO verificationReviewRequestDTO = new VerificationReviewRequestDTO();
    verificationReviewRequestDTO.setSchemaVersion(1);
    verificationReviewRequestDTO.setApplicationId(application);
    verificationReviewRequestDTO.setCaseId(reviewCommand.caseId());
    verificationReviewRequestDTO.setDecision(approve ? "approve" : "reject");
    verificationReviewRequestDTO.setExpectedVersion(reviewCommand.expectedVersion());
    verificationReviewRequestDTO.setExpectedCaseVersion(reviewCommand.expectedCaseVersion());
    verificationReviewRequestDTO.setRemark(remark);

    if (reviewCommand.validYears() != null) throw ApiFailure.invalid();
    VerificationReviewResultVO verificationReviewResultVOReplay =
        verificationCasework.replay(
            key,
            actor,
            application,
            verificationReviewRequestDTO,
            VerificationReviewResultVO.class);
    if (verificationReviewResultVOReplay != null) return verificationReviewResultVOReplay;
    verificationAccess.owner(realnameVerificationDOLocked.getUserId(), "realname", true);
    if (!CODE_PENDING.equals(realnameVerificationDOLocked.getVerificationStatus()))
      throw conflict(CODE_REALNAME_ALREADY_REVIEWED);
    if (!adminCaseDO.getId().equals(reviewCommand.caseId())
        || realnameVerificationDOLocked.getVersion() != reviewCommand.expectedVersion()
        || adminCaseDO.getVersion() != reviewCommand.expectedCaseVersion())
      throw conflict(CODE_VERSION_CONFLICT);
    LocalDateTime now = now(clock);
    LocalDateTime expiry = null;
    String status = approve ? CODE_VERIFIED : CODE_REJECTED;
    if (realnameMapper.reviewRealnameVerificationByVersion(
            application,
            realnameVerificationDOLocked.getVersion(),
            status,
            actor,
            remark,
            now,
            expiry)
        != 1) throw conflict(CODE_VERSION_CONFLICT);
    VerificationReviewResultVO verificationReviewResultVONew = new VerificationReviewResultVO();
    verificationReviewResultVONew.setActionId(Ids.next());
    verificationReviewResultVONew.setApplicationId(application);
    verificationReviewResultVONew.setStatus(status);
    verificationReviewResultVONew.setVersion(realnameVerificationDOLocked.getVersion() + 1);
    verificationReviewResultVONew.setCaseId(adminCaseDO.getId());
    verificationReviewResultVONew.setCaseVersion(adminCaseDO.getVersion() + 1);
    verificationReviewResultVONew.setReviewedAt(time(now));
    verificationReviewResultVONew.setReviewedByAdminUserId(actor);

    verificationCasework.finish(
        "realname",
        realnameVerificationDOLocked,
        adminCaseDO,
        actor,
        key,
        verificationReviewRequestDTO,
        verificationReviewResultVONew,
        trace,
        now);
    if (approve)
      firstRewardCaptureService.capture(com.mujian.backend.common.enums.FirstRewardEvent.first_realname_verified,
          realnameVerificationDOLocked.getUserId(), application, true, true, now);
    return verificationReviewResultVONew;
  }
}
