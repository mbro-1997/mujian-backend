package com.mujian.backend.realname.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.service.VerificationCasework;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.support.*;
import com.mujian.backend.infrastructure.crypto.VerificationCrypto;
import com.mujian.backend.material.service.*;
import com.mujian.backend.realname.domain.RealnameVerificationDO;
import com.mujian.backend.realname.mapper.RealnameMapper;
import com.mujian.backend.realname.policy.RealnamePolicy;
import com.mujian.backend.realname.projection.RealnameListProjection;
import com.mujian.backend.realname.service.*;
import com.mujian.backend.realname.vo.*;
import com.mujian.backend.verification.dto.VerificationListQueryDTO;
import java.time.*;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RealnameQueryServiceImpl implements RealnameQueryService {
  private final RealnameMapper realnameMapper;
  private final LoginMapper loginMapper;
  private final VerificationAccess verificationAccess;
  private final VerificationCrypto verificationCrypto;
  private final VerificationCasework verificationCasework;
  private final Clock clock;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public RealnameQueryServiceImpl(
      RealnameMapper realnameMapper,
      LoginMapper loginMapper,
      VerificationAccess verificationAccess,
      VerificationCrypto verificationCrypto,
      VerificationCasework verificationCasework,
      Clock clock) {
    this.realnameMapper = realnameMapper;
    this.loginMapper = loginMapper;
    this.verificationAccess = verificationAccess;
    this.verificationCrypto = verificationCrypto;
    this.verificationCasework = verificationCasework;
    this.clock = clock;
  }

  /** 按既有字段和脱敏规则组装认证申请响应。 */
  public RealnameApplicationVO application(RealnameVerificationDO realnameVerificationDO) {
    if (realnameVerificationDO == null) return null;
    String name =
        verificationCrypto.decrypt(
            realnameVerificationDO.getId(),
            "real_name_ciphertext",
            realnameVerificationDO.getEncryptionKeyVersion(),
            realnameVerificationDO.getRealNameCiphertext());
    String card =
        verificationCrypto.decrypt(
            realnameVerificationDO.getId(),
            "id_card_ciphertext",
            realnameVerificationDO.getEncryptionKeyVersion(),
            realnameVerificationDO.getIdCardCiphertext());
    String status = realnameVerificationDO.getVerificationStatus();
    return new RealnameApplicationVO()
        .id(realnameVerificationDO.getId())
        .applicationNo("RV" + realnameVerificationDO.getId())
        .status(status)
        .realNameMasked(RealnamePolicy.maskedName(name))
        .idCardMasked(RealnamePolicy.maskedCard(card))
        .submittedAt(time(realnameVerificationDO.getSubmittedAt()))
        .reviewedAt(time(realnameVerificationDO.getReviewedAt()))
        .verifiedAt(time(realnameVerificationDO.getVerifiedAt()))
        .rejectReason(realnameVerificationDO.getRejectReason())
        .nextAction(
            switch (status) {
              case CODE_PENDING -> CODE_WAIT_REVIEW;
              case CODE_VERIFIED -> CODE_VIEW_VERIFIED;
              case CODE_REJECTED -> CODE_RESUBMIT;
              default -> CODE_CONTACT_SUPPORT;
            });
  }

  /** 读取当前用户的有效认证记录。 */
  public RealnameVerificationDO currentRow(String user) {
    return realnameMapper.selectCurrentRealnameVerification(user, now(clock));
  }

  /** 组装当前认证申请，保持空申请语义。 */
  public RealnameApplicationVO currentApplication(String user) {
    return application(currentRow(user));
  }

  /** 只读取准入所需状态，避免取回证件密文。 */
  public String currentStatus(String user) {
    String status = realnameMapper.selectCurrentRealnameStatus(user);
    return status == null ? CODE_UNVERIFIED : status;
  }

  /** 读取当前生效记录并组装业务上下文。 */
  public RealnameCurrentVO current(String user) {
    boolean active = verificationAccess.owner(user, "realname", false);
    RealnameApplicationVO realnameApplicationVO = currentApplication(user);
    String status =
        realnameApplicationVO == null ? CODE_UNVERIFIED : realnameApplicationVO.getStatus();
    String next =
        !active
            ? CODE_ACCOUNT_RESTRICTED
            : switch (status) {
              case CODE_UNVERIFIED -> CODE_FILL_FORM;
              case CODE_PENDING -> CODE_WAIT_REVIEW;
              case CODE_VERIFIED -> CODE_VIEW_VERIFIED;
              case CODE_REJECTED -> CODE_RESUBMIT;
              default -> CODE_CONTACT_SUPPORT;
            };
    RealnameCurrentVO realnameCurrentVO =
        new RealnameCurrentVO()
            .application(realnameApplicationVO)
            .canSubmit(Set.of(CODE_FILL_FORM, CODE_RESUBMIT).contains(next))
            .nextAction(next)
            .consent(VerificationMaterials.consentView("realname"))
            .uploadPolicy(VerificationMaterials.policy());
    realnameCurrentVO.setPhoneMasked(maskPhone(loginMapper.selectUserPhoneById(user)));
    return realnameCurrentVO;
  }

  /** 按原请求编号恢复已提交结果并核对访问权限。 */
  public RealnameApplicationVO recover(String user, String key) {
    verificationAccess.owner(user, "realname", false);
    RealnameVerificationDO realnameVerificationDO =
        realnameMapper.selectRealnameVerificationByRequest(id(key));
    if (realnameVerificationDO == null || !user.equals(realnameVerificationDO.getUserId()))
      throw missing();
    return application(realnameVerificationDO);
  }

  /** 检查审核权限后返回申请详情和工单信息。 */
  public RealnameDetailVO detail(String actor, String id) {
    java.util.List<String> permissions = verificationAccess.readPermissions(actor, "admin:realname:read");
    RealnameVerificationDO realnameVerificationDO =
        realnameMapper.selectRealnameVerificationById(id(id));
    if (realnameVerificationDO == null) throw missing();
    AdminCaseDO adminCaseDO =
        verificationCasework.associated("realname", realnameVerificationDO, false);
    RealnameDetailVO realnameDetailVO =
        new RealnameDetailVO()
            .application(application(realnameVerificationDO))
            .userId(realnameVerificationDO.getUserId())
            .nickname(loginMapper.selectUserNicknameById(realnameVerificationDO.getUserId()))
            .phoneMasked(
                maskPhone(loginMapper.selectUserPhoneById(realnameVerificationDO.getUserId())))
            .caseId(adminCaseDO.getId())
            .caseVersion(adminCaseDO.getVersion())
            .version(realnameVerificationDO.getVersion())
            .reviewedByAdminUserId(realnameVerificationDO.getReviewedByAdminUserId())
            .auditRemark(realnameVerificationDO.getAuditRemark())
            .canViewSensitive(permissions.contains("admin:realname:sensitive"))
            .canReview(
                permissions.contains("admin:realname:review")
                    && CODE_PENDING.equals(realnameVerificationDO.getVerificationStatus())
                    && ownerActive(realnameVerificationDO.getUserId()));
    return realnameDetailVO;
  }

  /** 确认申请所属账号处于允许审核的状态。 */
  private boolean ownerActive(String user) {
    try {
      return verificationAccess.owner(user, "realname", false);
    } catch (ApiFailure apiFailure) {
      return false;
    }
  }

  /** 校验筛选后执行数据库分页，仅组装列表所需字段。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<RealnameListItemVO> list(
      String actor, VerificationListQueryDTO verificationListQueryDTO) {
    verificationAccess.readPermissions(actor, "admin:realname:read");
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
                ? CODE_PENDING
                : verificationListQueryDTO.getStatus(),
            verificationListQueryDTO.getKeyword(),
            from,
            to,
            page,
            size,
            now(clock));
    long total = realnameMapper.selectRealnameVerificationCountByQuery(verificationFilter);
    List<RealnameListProjection> rows =
        verificationFilter.offset() >= total
            ? List.of()
            : realnameMapper.selectRealnameVerificationPageByQuery(verificationFilter);
    List<RealnameListItemVO> items = new ArrayList<>();
    for (RealnameListProjection realnameListProjection : rows) {
      String realName =
          verificationCrypto.decrypt(
              realnameListProjection.getId(),
              "real_name_ciphertext",
              realnameListProjection.getEncryptionKeyVersion(),
              realnameListProjection.getRealNameCiphertext());
      items.add(
          new RealnameListItemVO()
              .id(realnameListProjection.getId())
              .applicationNo("RV" + realnameListProjection.getId())
              .userId(realnameListProjection.getUserId())
              .nickname(realnameListProjection.getNickname())
              .status(realnameListProjection.getVerificationStatus())
              .submittedAt(time(realnameListProjection.getSubmittedAt()))
              .realNameMasked(RealnamePolicy.maskedName(realName)));
    }
    return new PageResult<>(items, Long.toString(total), page, size);
  }
}
