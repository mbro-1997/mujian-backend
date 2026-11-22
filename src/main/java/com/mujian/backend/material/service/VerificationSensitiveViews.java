package com.mujian.backend.material.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.service.VerificationCasework;
import com.mujian.backend.infrastructure.crypto.VerificationCrypto;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore;
import com.mujian.backend.material.vo.*;
import com.mujian.backend.realname.domain.RealnameVerificationDO;
import com.mujian.backend.realname.mapper.RealnameMapper;
import com.mujian.backend.student.domain.StudentVerificationDO;
import com.mujian.backend.student.mapper.StudentMapper;
import com.mujian.backend.verification.domain.VerificationApplicationDO;
import java.time.*;
import java.time.Instant;
import java.util.*;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class VerificationSensitiveViews {
  private final RealnameMapper realnameMapper;
  private final StudentMapper studentMapper;
  private final LoginMapper loginMapper;
  private final VerificationAccess verificationAccess;
  private final VerificationCrypto verificationCrypto;
  private final PrivateMaterialStore privateMaterialStore;
  private final VerificationCasework verificationCasework;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public VerificationSensitiveViews(
      RealnameMapper realnameMapper,
      StudentMapper studentMapper,
      LoginMapper loginMapper,
      VerificationAccess verificationAccess,
      VerificationCrypto verificationCrypto,
      PrivateMaterialStore privateMaterialStore,
      VerificationCasework verificationCasework,
      Clock clock,
      ObjectMapper objectMapper) {
    this.realnameMapper = realnameMapper;
    this.studentMapper = studentMapper;
    this.loginMapper = loginMapper;
    this.verificationAccess = verificationAccess;
    this.verificationCrypto = verificationCrypto;
    this.privateMaterialStore = privateMaterialStore;
    this.verificationCasework = verificationCasework;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  /** 核对敏感资料访问所需业务权限。 */
  private void require(String actor, String domain) {
    verificationAccess.admin(actor, "admin:" + domain + ":read");
    verificationAccess.admin(actor, "admin:" + domain + ":sensitive");
  }

  /** 解密姓名，仅供已授权的业务处理使用。 */
  private String name(RealnameVerificationDO realnameVerificationDO) {
    return verificationCrypto.decrypt(
        realnameVerificationDO.getId(),
        "real_name_ciphertext",
        realnameVerificationDO.getEncryptionKeyVersion(),
        realnameVerificationDO.getRealNameCiphertext());
  }

  /** 解密证件号码，仅供已授权的业务处理使用。 */
  private String card(RealnameVerificationDO realnameVerificationDO) {
    return verificationCrypto.decrypt(
        realnameVerificationDO.getId(),
        "id_card_ciphertext",
        realnameVerificationDO.getEncryptionKeyVersion(),
        realnameVerificationDO.getIdCardCiphertext());
  }

  /** 记录敏感资料查看审计。 */
  private String audit(
      String domain,
      VerificationApplicationDO verificationApplicationDO,
      String actor,
      String trace,
      LocalDateTime expiry) {
    return verificationCasework.audit(
        domain,
        verificationApplicationDO.getId(),
        actor,
        domain + "_sensitive_view",
        "admin:" + domain + ":sensitive",
        trace,
        null,
        null,
        null,
        null,
        null,
        view(
            "schemaVersion",
            1,
            "fieldTypes",
            domain.equals("realname")
                ? List.of("name", "id_card", "images")
                : List.of("phone", "images"),
            "authorizationExpiresAt",
            time(expiry)),
        now(clock));
  }

  /** 按权限返回实名敏感字段及短时材料链接。 */
  public RealnameSensitiveVO realname(String actor, String id, String trace) {
    require(actor, "realname");
    RealnameVerificationDO realnameVerificationDO =
        realnameMapper.selectRealnameVerificationById(id(id));
    if (realnameVerificationDO == null) throw missing();
    LocalDateTime expiry = now(clock).plusSeconds(120);
    Instant until = expiry.atZone(ZONE).toInstant();
    String fullName = name(realnameVerificationDO), fullCard = card(realnameVerificationDO);
    String audit = audit("realname", realnameVerificationDO, actor, trace, expiry);
    String
        front = privateMaterialStore.sign(realnameVerificationDO.getIdCardFrontObjectKey(), until),
        back = privateMaterialStore.sign(realnameVerificationDO.getIdCardBackObjectKey(), until);
    return new RealnameSensitiveVO()
        .applicationId(id)
        .fullName(fullName)
        .fullIdCard(fullCard)
        .frontUrl(front)
        .backUrl(back)
        .expiresAt(time(expiry))
        .auditId(audit);
  }

  /** 按权限返回学生材料及必要实名信息。 */
  public StudentSensitiveVO student(String actor, String id, String trace) {
    require(actor, "student");
    StudentVerificationDO studentVerificationDO =
        studentMapper.selectStudentVerificationById(id(id));
    if (studentVerificationDO == null) throw missing();
    LocalDateTime expiry = now(clock).plusSeconds(120);
    ArrayList<SensitiveImageVO> images = new ArrayList<>();
    RealnameSensitiveAccessVO realnameSensitiveAccessVO =
        new RealnameSensitiveAccessVO.Restricted(CODE_NOT_AUTHORIZED);
    if (verificationAccess.has(actor, "admin:realname:read")
        && verificationAccess.has(actor, "admin:realname:sensitive")) {
      RealnameVerificationDO realnameVerificationDO =
          realnameMapper.selectCurrentRealnameVerification(
              studentVerificationDO.getUserId(), now(clock));
      if (realnameVerificationDO == null)
        realnameSensitiveAccessVO = new RealnameSensitiveAccessVO.Restricted(CODE_NOT_AVAILABLE);
      else {
        String n = name(realnameVerificationDO), c = card(realnameVerificationDO);
        String a = audit("realname", realnameVerificationDO, actor, trace, expiry);
        realnameSensitiveAccessVO =
            new RealnameSensitiveAccessVO.Granted(
                CODE_GRANTED, realnameVerificationDO.getId(), n, c, a);
      }
    }
    String audit = audit("student", studentVerificationDO, actor, trace, expiry);
    int pos = 0;
    for (JsonNode jsonNode : objectMapper.readTree(studentVerificationDO.getStudentCardUrls()))
      images.add(
          new SensitiveImageVO()
              .position(++pos)
              .url(
                  privateMaterialStore.sign(jsonNode.asString(), expiry.atZone(ZONE).toInstant())));
    return new StudentSensitiveVO()
        .applicationId(id)
        .phone(loginMapper.selectUserPhoneById(studentVerificationDO.getUserId()))
        .images(images)
        .realnameView(realnameSensitiveAccessVO)
        .expiresAt(time(expiry))
        .auditId(audit);
  }
}
