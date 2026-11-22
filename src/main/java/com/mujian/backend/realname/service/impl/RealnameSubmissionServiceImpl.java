package com.mujian.backend.realname.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.service.VerificationCasework;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.*;
import com.mujian.backend.infrastructure.crypto.VerificationCrypto;
import com.mujian.backend.material.service.*;
import com.mujian.backend.material.service.VerificationMaterials.Receipt;
import com.mujian.backend.realname.domain.RealnameVerificationDO;
import com.mujian.backend.realname.mapper.RealnameMapper;
import com.mujian.backend.realname.policy.RealnamePolicy;
import com.mujian.backend.realname.service.*;
import com.mujian.backend.realname.vo.RealnameApplicationVO;
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
public class RealnameSubmissionServiceImpl implements RealnameSubmissionService {

  private final RealnameMapper realnameMapper;
  private final LoginMapper loginMapper;
  private final VerificationAccess verificationAccess;
  private final VerificationMaterials verificationMaterials;
  private final VerificationCrypto verificationCrypto;
  private final VerificationCasework verificationCasework;
  private final RealnameQueryService realnameQueryService;
  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public RealnameSubmissionServiceImpl(
      RealnameMapper realnameMapper,
      LoginMapper loginMapper,
      VerificationAccess verificationAccess,
      VerificationMaterials verificationMaterials,
      VerificationCrypto verificationCrypto,
      VerificationCasework verificationCasework,
      RealnameQueryService realnameQueryService,
      Clock clock,
      ObjectMapper objectMapper,
      PlatformTransactionManager platformTransactionManager) {
    this.realnameMapper = realnameMapper;
    this.loginMapper = loginMapper;
    this.verificationAccess = verificationAccess;
    this.verificationMaterials = verificationMaterials;
    this.verificationCrypto = verificationCrypto;
    this.verificationCasework = verificationCasework;
    this.realnameQueryService = realnameQueryService;
    this.clock = clock;
    this.objectMapper = objectMapper;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    transactionTemplate.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 核对重试材料与原申请一致后返回原结果。 */
  private VerificationSubmitVO<RealnameApplicationVO> replay(
      String user,
      RealnameVerificationDO realnameVerificationDOOld,
      String name,
      String card,
      List<String> keys,
      String version) {
    if (!user.equals(realnameVerificationDOOld.getUserId())) throw missing();
    if (!version.equals(realnameVerificationDOOld.getMaterialConsentVersion())
        || !name.equals(
            verificationCrypto.decrypt(
                realnameVerificationDOOld.getId(),
                "real_name_ciphertext",
                realnameVerificationDOOld.getEncryptionKeyVersion(),
                realnameVerificationDOOld.getRealNameCiphertext()))
        || !card.equals(
            verificationCrypto.decrypt(
                realnameVerificationDOOld.getId(),
                "id_card_ciphertext",
                realnameVerificationDOOld.getEncryptionKeyVersion(),
                realnameVerificationDOOld.getIdCardCiphertext()))
        || !keys.equals(
            List.of(
                realnameVerificationDOOld.getIdCardFrontObjectKey(),
                realnameVerificationDOOld.getIdCardBackObjectKey())))
      throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
    return new VerificationSubmitVO<>(
        CODE_REPLAYED, realnameQueryService.application(realnameVerificationDOOld));
  }

  /** 核对幂等与待审状态，在原事务内保存认证申请。 */
  public VerificationSubmitVO<RealnameApplicationVO> submit(
      String user, String key, SubmitRequest submitRequest) {
    id(key);
    verificationAccess.owner(user, "realname", false);
    String name = RealnamePolicy.name(submitRequest.realName()),
        card = RealnamePolicy.card(submitRequest.idCard());
    List<String> uploads =
        List.of(id(submitRequest.frontUploadId()), id(submitRequest.backUploadId()));
    if (uploads.size() < 2 || uploads.size() > 2 || new HashSet<>(uploads).size() != uploads.size())
      throw ApiFailure.invalid();
    List<String> keys = new ArrayList<>();
    for (String uploadId : uploads) keys.add(VerificationMaterials.key(user, "realname", uploadId));
    RealnameVerificationDO realnameVerificationDOOld =
        realnameMapper.selectRealnameVerificationByRequest(key);
    if (realnameVerificationDOOld != null)
      return replay(
          user, realnameVerificationDOOld, name, card, keys, submitRequest.consentVersion());
    verificationAccess.owner(user, "realname", true);
    VerificationMaterials.consent(
        "realname", submitRequest.consentVersion(), submitRequest.consentAccepted());
    VerificationSubmitVO<RealnameApplicationVO> verificationSubmitVO =
        transactionTemplate.execute(
            s -> {
              loginMapper.selectUserByIdForUpdate(user);
              verificationAccess.owner(user, "realname", false);
              RealnameVerificationDO realnameVerificationDOCommitted =
                  realnameMapper.selectRealnameVerificationByRequest(key);
              if (realnameVerificationDOCommitted != null)
                return replay(
                    user,
                    realnameVerificationDOCommitted,
                    name,
                    card,
                    keys,
                    submitRequest.consentVersion());
              verificationAccess.owner(user, "realname", true);
              RealnameVerificationDO realnameVerificationDOCurrent =
                  realnameMapper.selectCurrentRealnameVerification(user, now(clock));
              if (realnameVerificationDOCurrent != null) {
                String status =
                    realnameQueryService.application(realnameVerificationDOCurrent).getStatus();
                if (status.equals(CODE_PENDING) || status.equals(CODE_VERIFIED))
                  return new VerificationSubmitVO<>(
                      status.equals(CODE_PENDING) ? CODE_EXISTING_PENDING : CODE_ALREADY_VERIFIED,
                      realnameQueryService.application(realnameVerificationDOCurrent));
              }
              return null;
            });
    if (verificationSubmitVO != null) return verificationSubmitVO;
    List<VerificationMaterials.Receipt> receipts = new ArrayList<>();
    try {
      for (int i = 0; i < uploads.size(); i++)
        receipts.add(
            verificationMaterials.receipt(
                user,
                (i == 0 ? "realname_front" : "realname_back"),
                submitRequest.consentVersion(),
                uploads.get(i)));
    } catch (ApiFailure apiFailure) {
      RealnameVerificationDO committed = realnameMapper.selectRealnameVerificationByRequest(key);
      if (committed != null)
        return replay(user, committed, name, card, keys, submitRequest.consentVersion());
      throw apiFailure;
    }
    try {
      return transactionTemplate.execute(
          s -> {
            loginMapper.selectUserByIdForUpdate(user);
            verificationAccess.owner(user, "realname", false);
            RealnameVerificationDO committed =
                realnameMapper.selectRealnameVerificationByRequest(key);
            if (committed != null)
              return replay(user, committed, name, card, keys, submitRequest.consentVersion());
            verificationAccess.owner(user, "realname", true);
            RealnameVerificationDO current =
                realnameMapper.selectCurrentRealnameVerification(user, now(clock));
            if (current != null) {
              String status = realnameQueryService.application(current).getStatus();
              if (status.equals(CODE_PENDING) || status.equals(CODE_VERIFIED))
                return new VerificationSubmitVO<>(
                    status.equals(CODE_PENDING) ? CODE_EXISTING_PENDING : CODE_ALREADY_VERIFIED,
                    realnameQueryService.application(current));
            }
            for (Receipt receipt : receipts) verificationMaterials.validateNewReference(receipt);
            LocalDateTime now = now(clock);
            String application = Ids.next();
            Instant acceptedAt = null;
            for (Receipt receipt : receipts) {
              if (acceptedAt == null || receipt.acceptedAt().isBefore(acceptedAt))
                acceptedAt = receipt.acceptedAt();
            }
            if (acceptedAt == null) throw new NoSuchElementException();
            RealnameVerificationDO realnameVerificationDO = new RealnameVerificationDO();
            realnameVerificationDO.setId(application);
            realnameVerificationDO.setUserId(user);
            realnameVerificationDO.setSubmitRequestId(key);
            realnameVerificationDO.setSubmittedAt(now);
            realnameVerificationDO.setMaterialConsentVersion(submitRequest.consentVersion());
            realnameVerificationDO.setMaterialConsentAt(LocalDateTime.ofInstant(acceptedAt, ZONE));
            realnameVerificationDO.setVerificationStatus(CODE_PENDING);
            realnameVerificationDO.setCreatedAt(now);
            realnameVerificationDO.setUpdatedAt(now);

            String version = verificationCrypto.version();
            realnameVerificationDO.setRealNameCiphertext(
                verificationCrypto.encrypt(application, "real_name_ciphertext", version, name));
            realnameVerificationDO.setIdCardCiphertext(
                verificationCrypto.encrypt(application, "id_card_ciphertext", version, card));
            realnameVerificationDO.setEncryptionKeyVersion(version);
            realnameVerificationDO.setIdCardFrontObjectKey(keys.get(0));
            realnameVerificationDO.setIdCardBackObjectKey(keys.get(1));
            realnameVerificationDO.setProvider("manual_admin");

            realnameMapper.insertRealnameVerification(realnameVerificationDO);
            verificationCasework.create("realname", user, application, now);
            return new VerificationSubmitVO<>(
                CODE_CREATED,
                realnameQueryService.application(
                    realnameMapper.selectRealnameVerificationById(application)));
          });
    } catch (org.springframework.dao.DuplicateKeyException duplicateKeyException) {
      RealnameVerificationDO committed = realnameMapper.selectRealnameVerificationByRequest(key);
      if (committed != null)
        return replay(user, committed, name, card, keys, submitRequest.consentVersion());
      throw duplicateKeyException;
    }
  }
}
