package com.mujian.backend.material.service;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.enums.PropReasonCode;

import com.mujian.backend.common.enums.FulfillmentReason;

import com.mujian.backend.common.enums.FactoryReasonCode;

import org.apache.pdfbox.pdmodel.PDDocument;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.D2RateLimiter;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.infrastructure.storage.*;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.Stored;
import com.mujian.backend.material.mapper.MaterialReferenceMapper;
import com.mujian.backend.material.vo.*;
import com.mujian.backend.web.response.account.ContextResponse;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class VerificationMaterials {
  public static final String REALNAME_CONSENT = "我同意平台为订单履约、押金风控和争议处理目的使用实名信息，并按隐私协议保护个人敏感信息。";
  public static final String STUDENT_CONSENT = "我确认提交的学生证材料真实有效，并同意平台用于学生身份审核。";

  public record Receipt(
      String owner,
      String purpose,
      String uploadId,
      String objectKey,
      String consentVersion,
      Instant acceptedAt,
      Instant acceptUntil,
      EvidenceScope evidenceScope) {
    /** 兼容已有认证、工厂和道具材料凭证。 */
    public Receipt(
        String owner,
        String purpose,
        String uploadId,
        String objectKey,
        String consentVersion,
        Instant acceptedAt,
        Instant acceptUntil) {
      this(owner, purpose, uploadId, objectKey, consentVersion, acceptedAt, acceptUntil, null);
    }
  }

  /** 订单材料在上传时冻结订单、节点和实际角色。 */
  public record EvidenceScope(String orderId, String node, String role) {}

  /** 对象版本取自实际HEAD，用于引用原文件。 */
  public record VerifiedObject(Receipt receipt, String versionId) {}

  public record OrderUpload(Receipt receipt, String mimeType, int sizeBytes) {}

  private final PrivateMaterialStore privateMaterialStore;
  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final VerificationAccess verificationAccess;
  private final MaterialReferenceMapper materialReferenceMapper;
  private final D2RateLimiter d2RateLimiter;
  private final String prefix;
  private final com.mujian.backend.factory.service.FactoryAccess factoryAccess;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public VerificationMaterials(
      PrivateMaterialStore privateMaterialStore,
      StringRedisTemplate stringRedisTemplate,
      ObjectMapper objectMapper,
      Clock clock,
      VerificationAccess verificationAccess,
      MaterialReferenceMapper materialReferenceMapper,
      D2RateLimiter d2RateLimiter,
      com.mujian.backend.factory.service.FactoryAccess factoryAccess,
      @Value("${mj.environment:production}") String env) {
    this.factoryAccess = factoryAccess;
    this.privateMaterialStore = privateMaterialStore;
    this.stringRedisTemplate = stringRedisTemplate;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.verificationAccess = verificationAccess;
    this.materialReferenceMapper = materialReferenceMapper;
    this.d2RateLimiter = d2RateLimiter;
    prefix = "mujian:" + env + ":d2upload:";
  }

  /** 将材料用途映射到既定存储域。 */
  public static String domain(String purpose) {
    return switch (purpose) {
      case "realname_front", "realname_back" -> "realname";
      case "student_card" -> "student";
      case "factory_avatar", "business_license", "factory_photo", "service_case" -> "factory";
      case "prop_image" -> "props";
      case "custom_material" -> "custom-material";
      case "order_evidence" -> "order-evidence";
      case "qr_exception_evidence" -> "qr-evidence";
      default -> throw new IllegalArgumentException("unknown purpose");
    };
  }

  /** 取得对应材料用途的告知版本。 */
  public static void consent(String domain, String version, boolean accepted) {
    if (!accepted) throw ApiFailure.invalid();
    if (!(domain + "-material-v1").equals(version)) throw conflict(CODE_CONSENT_VERSION_CHANGED);
  }

  /** 返回认证材料用途、大小及凭证时效约束。 */
  public static MaterialPolicyVO policy() {
    return new MaterialPolicyVO()
        .contentTypes(List.of(CODE_IMAGE_JPEG, CODE_IMAGE_PNG))
        .maxBytes(5242880)
        .maxPixels(20000000)
        .receiptTtlSeconds(86400);
  }

  /** 返回材料告知内容及当前版本。 */
  public static MaterialConsentVO consentView(String domain) {
    return new MaterialConsentVO()
        .version(domain + "-material-v1")
        .text(domain.equals("realname") ? REALNAME_CONSENT : STUDENT_CONSENT);
  }

  /** 生成或解析材料的稳定对象定位键。 */
  public static String key(String owner, String domain, String upload) {
    if (domain.equals("custom-material")) return CosMaterialStore.CUSTOM_PREFIX + id(owner) + "/" + id(upload);
    if (domain.equals("order-evidence"))
      return CosMaterialStore.ORDER_EVIDENCE_PREFIX + id(owner) + "/" + id(upload);
    if (Set.of("props", "qr-evidence").contains(domain))
      return "dev/d2-c/" + domain + "/" + id(owner) + "/" + id(upload);
    return (domain.equals("factory")
            ? CosMaterialStore.FACTORY_PREFIX
            : CosMaterialStore.PREFIX + domain + "/")
        + id(owner)
        + "/"
        + id(upload);
  }

  /** 校验用途、身份和文件后复用私有材料上传。 */
  public Receipt upload(
      String owner, String purpose, String version, boolean accepted, byte[] bytes) {
    String d = domain(purpose);
    verificationAccess.owner(owner, d, true);
    consent(d, version, accepted);
    d2RateLimiter.check("upload", owner, 10);
    return storeUpload(owner, purpose, version, bytes);
  }

  /** 按工厂用途复用上传凭证与归属检查。 */
  public Receipt uploadFactory(String owner, String purpose, byte[] bytes) {
    if (!domain(purpose).equals("factory")) throw ApiFailure.invalid();
    ContextResponse contextResponse = factoryAccess.context(owner, true);
    if (contextResponse.roleCode() != null && !CODE_SUPPLIER.equals(contextResponse.roleCode()))
      throw ApiFailure.denied(FactoryReasonCode.FACTORY_ROLE_NOT_ELIGIBLE.name());
    d2RateLimiter.check("factory-upload", owner, 40);
    return storeUpload(owner, purpose, "factory-material-v1", bytes);
  }

  /** 按道具用途复用上传凭证与归属检查。 */
  public Receipt uploadProp(String owner, String purpose, byte[] bytes) {
    if ("prop_image".equals(purpose)) {
      com.mujian.backend.factory.projection.FactorySourceProjection factorySourceProjection =
          factoryAccess.profile(owner, true, false);
      if (!CODE_ACTIVE.equals(factorySourceProjection.getFactoryStatus()))
        throw ApiFailure.denied(PropReasonCode.FACTORY_NOT_OPERATING.name());
    } else if ("qr_exception_evidence".equals(purpose))
      factoryAccess.admin(owner, "prop-qr", "manage");
    else throw ApiFailure.invalid();
    d2RateLimiter.check("factory-upload", owner, 40);
    return storeUpload(owner, purpose, domain(purpose) + "-material-v1", bytes);
  }

  /** 保存私有对象并登记有期限的上传凭证。 */
  private Receipt storeUpload(String owner, String purpose, String version, byte[] bytes) {
    return storeUpload(owner, purpose, version, bytes, imageType(bytes), null);
  }

  /** 已通过订单归属校验的照片沿既有私有存储及24小时凭证机制保存。 */
  public OrderUpload uploadOrder(String owner, EvidenceScope scope, byte[] bytes) {
    id(scope.orderId());
    d2RateLimiter.check("order-evidence-upload", owner, 40);
    String type = imageType(bytes);
    return new OrderUpload(
        storeUpload(owner, "order_evidence", "order-evidence-material-v1", bytes, type, scope),
        type,
        bytes.length);
  }

  /** 绑定本次定制材料上传的用途、文件类型和内容摘要。 */
  public Receipt uploadCustom(String owner,EvidenceScope scope,byte[] bytes,boolean pdf) {
    d2RateLimiter.check("custom-material-upload",owner,40);
    String type;
    try {
      if(pdf){try(PDDocument doc=org.apache.pdfbox.Loader.loadPDF(bytes)){if(doc.isEncrypted() || doc.getNumberOfPages()<1)throw new java.io.IOException("invalid PDF");}type="application/pdf";}
      else type=imageType(bytes);
    }catch(java.io.IOException e){throw new ApiFailure(415,D2Codes.CODE_INVALID_ARGUMENT,D2Codes.CODE_MATERIAL_TYPE_UNSUPPORTED);}
    catch(ApiFailure e){throw new ApiFailure(e.reason.equals(D2Codes.CODE_MATERIAL_TOO_LARGE)?413:415,D2Codes.CODE_INVALID_ARGUMENT,e.reason);}
    try{return storeUpload(owner,"custom_material","custom-material-material-v1",bytes,type,scope);}
    catch(ApiFailure e){if(D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE.equals(e.reason))throw unavailable(FulfillmentReason.PRIVATE_STORAGE_UNAVAILABLE.name());throw e;}
  }

  /** 专用域元数据与凭证同源，未绑定对象由现有清理任务回收。 */
  private Receipt storeUpload(
      String owner,
      String purpose,
      String version,
      byte[] bytes,
      String type,
      EvidenceScope scope) {
    String d = domain(purpose);
    Instant at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
        until = at.plus(Duration.ofHours(24));
    String id = Ids.next(), key = key(owner, d, id);
    VerificationMaterials.Receipt verificationMaterialsReceipt =
        new Receipt(owner, purpose, id, key, version, at, until, scope);
    Map<String, String> metadata =
        new HashMap<>(
            Map.of(
                "owner",
                owner,
                "purpose",
                purpose,
                "upload-id",
                id,
                "consent-version",
                version,
                "accepted-at",
                at.toString(),
                "accept-until",
                until.toString()));
    if (scope != null) {
      metadata.put("order-id", scope.orderId());
      metadata.put("evidence-node", scope.node());
      metadata.put("operator-role", scope.role());
    }
    privateMaterialStore.put(key, bytes, type, metadata);
    try {
      Duration ttl = Duration.between(clock.instant(), until);
      if (ttl.isNegative() || ttl.isZero()) throw unavailable(CODE_OBJECT_STORAGE_UNAVAILABLE);
      stringRedisTemplate
          .opsForValue()
          .set(prefix + id, objectMapper.writeValueAsString(verificationMaterialsReceipt), ttl);
    } catch (ApiFailure apiFailure) {
      throw apiFailure;
    } catch (Exception e) {
      throw ApiFailure.redis().causedBy(e);
    }
    return verificationMaterialsReceipt;
  }

  /** 校验上传凭证并核对实际存储对象。 */
  public Receipt receipt(String owner, String purpose, String version, String upload) {
    return verified(owner, purpose, version, upload).receipt();
  }

  /** 事务外核对私有对象及其不可变元数据并带回真实版本。 */
  public VerifiedObject verified(String owner, String purpose, String version, String upload) {
    VerificationMaterials.Receipt verificationMaterialsReceipt = localReceipt(owner, purpose, version, upload);
    Stored stored = privateMaterialStore.head(verificationMaterialsReceipt.objectKey());
    if (!metadataValid(stored, verificationMaterialsReceipt))
      throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_REFERENCE_INVALID);
    return new VerifiedObject(verificationMaterialsReceipt, stored.versionId());
  }

  /**
   * C preflights COS before its owner-locked transaction; immutable receipt is re-read under lock.
   */
  public Receipt localReceipt(String owner, String purpose, String version, String upload) {
    String raw;
    try {
      raw = stringRedisTemplate.opsForValue().get(prefix + id(upload));
    } catch (Exception e) {
      throw ApiFailure.redis().causedBy(e);
    }
    if (raw == null)
      throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_REFERENCE_EXPIRED);
    Receipt verificationMaterialsReceipt;
    try {
      verificationMaterialsReceipt = objectMapper.readValue(raw, Receipt.class);
    } catch (Exception e) {
      throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_REFERENCE_INVALID);
    }
    if (!owner.equals(verificationMaterialsReceipt.owner())
        || !purpose.equals(verificationMaterialsReceipt.purpose())
        || !version.equals(verificationMaterialsReceipt.consentVersion())
        || !key(owner, domain(purpose), upload).equals(verificationMaterialsReceipt.objectKey()))
      throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_REFERENCE_INVALID);
    return verificationMaterialsReceipt;
  }

  /** 核对对象元数据与材料凭证一致。 */
  public boolean metadataValid(PrivateMaterialStore.Stored stored, Receipt verificationMaterialsReceipt) {
    Map<String, String> m = stored.metadata();
    EvidenceScope scope = verificationMaterialsReceipt.evidenceScope();
    boolean matchesScope =
        scope == null
            ? !"order_evidence".equals(verificationMaterialsReceipt.purpose())
            : scope.orderId().equals(m.get("order-id"))
                && scope.node().equals(m.get("evidence-node"))
                && scope.role().equals(m.get("operator-role"));
    return matchesScope
        && verificationMaterialsReceipt.owner().equals(m.get("owner"))
        && verificationMaterialsReceipt.purpose().equals(m.get("purpose"))
        && verificationMaterialsReceipt.uploadId().equals(m.get("upload-id"))
        && verificationMaterialsReceipt.consentVersion().equals(m.get("consent-version"))
        && verificationMaterialsReceipt.acceptedAt().toString().equals(m.get("accepted-at"))
        && verificationMaterialsReceipt.acceptUntil().toString().equals(m.get("accept-until"))
        && verificationMaterialsReceipt.acceptUntil().equals(verificationMaterialsReceipt.acceptedAt().plus(Duration.ofHours(24)));
  }

  /** Called only while holding the same owner row lock used by cleanup. */
  public void validateNewReference(Receipt verificationMaterialsReceipt) {
    if (!clock.instant().isBefore(verificationMaterialsReceipt.acceptUntil()))
      throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_REFERENCE_EXPIRED);
    if (materialReferenceMapper.countMaterialReferencesByOwnerAndKey(
            verificationMaterialsReceipt.owner(), verificationMaterialsReceipt.objectKey())
        > 0) throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_REFERENCE_INVALID);
  }

  /** 从文件内容识别允许的图片类型。 */
  public static String imageType(byte[] bytes) {
    if (bytes.length > 5242880)
      throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_TOO_LARGE);
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (input == null) throw new IOException();
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException();
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String type = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!Set.of("jpeg", "png").contains(type)) throw new IOException();
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels <= 0 || pixels > 20000000)
          throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_TOO_LARGE);
        if (reader.read(0) == null) throw new IOException();
        return "image/" + type;
      } finally {
        reader.dispose();
      }
    } catch (ApiFailure apiFailure) {
      throw apiFailure;
    } catch (Exception e) {
      throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_TYPE_UNSUPPORTED);
    }
  }
}
