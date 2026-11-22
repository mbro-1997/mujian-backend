package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.MaterialLocator;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.projection.*;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore;
import com.mujian.backend.material.service.VerificationMaterials;
import com.mujian.backend.material.service.VerificationMaterials.Receipt;
import java.time.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class FactoryMaterials {
  private final FactoryAccess factoryAccess;
  private final VerificationMaterials verificationMaterials;
  private final FactorySnapshotData factorySnapshotData;
  private final FactoryReviewMapper factoryReviewMapper;
  private final PrivateMaterialStore privateMaterialStore;
  private final FactoryAudit factoryAudit;
  private final Clock clock;
  private final StringRedisTemplate stringRedisTemplate;
  private final String prefix;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryMaterials(
      FactoryAccess factoryAccess,
      VerificationMaterials verificationMaterials,
      FactorySnapshotData factorySnapshotData,
      FactoryReviewMapper factoryReviewMapper,
      PrivateMaterialStore privateMaterialStore,
      FactoryAudit factoryAudit,
      Clock clock,
      StringRedisTemplate stringRedisTemplate,
      @Value("${mj.environment:production}") String env) {
    this.factoryAccess = factoryAccess;
    this.verificationMaterials = verificationMaterials;
    this.factorySnapshotData = factorySnapshotData;
    this.factoryReviewMapper = factoryReviewMapper;
    this.privateMaterialStore = privateMaterialStore;
    this.factoryAudit = factoryAudit;
    this.clock = clock;
    this.stringRedisTemplate = stringRedisTemplate;
    prefix = "mujian:" + env + ":factory-inflight:";
  }

  /** 校验用途、身份和文件后复用私有材料上传。 */
  public UploadResult upload(String owner, String purpose, byte[] bytes) {
    if (!Set.of("factory_avatar", "business_license", "factory_photo", "service_case")
        .contains(purpose)) throw ApiFailure.invalid();
    return admitted(owner, () -> verificationMaterials.uploadFactory(owner, purpose, bytes));
  }

  /** 按道具用途复用上传凭证与归属检查。 */
  public UploadResult uploadProp(String owner, String purpose, byte[] bytes) {
    return admitted(owner, () -> verificationMaterials.uploadProp(owner, purpose, bytes));
  }

  /** 检查账号是否允许使用当前工厂材料入口。 */
  private UploadResult admitted(
      String owner, java.util.function.Supplier<VerificationMaterials.Receipt> upload) {
    String key = prefix + owner, lease = com.mujian.backend.common.support.Ids.next();
    long now = System.currentTimeMillis();
    Long admitted;
    try {
      admitted =
          stringRedisTemplate.execute(
              new DefaultRedisScript<Long>(
                  "redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',ARGV[1]); if"
                      + " redis.call('ZCARD',KEYS[1])>=2 then return 0 end;"
                      + " redis.call('ZADD',KEYS[1],ARGV[2],ARGV[3]);"
                      + " redis.call('EXPIRE',KEYS[1],60); return 1",
                  Long.class),
              List.of(key),
              Long.toString(now),
              Long.toString(now + 60000),
              lease);
    } catch (Exception e) {
      throw ApiFailure.redis().causedBy(e);
    }
    if (!Long.valueOf(1).equals(admitted))
      throw new ApiFailure(429, CODE_RATE_LIMITED, CODE_TOO_MANY_REQUESTS, 1);
    try {
      Receipt receipt = upload.get();
      return new UploadResult(
          receipt.uploadId(), time(LocalDateTime.ofInstant(receipt.acceptUntil(), ZONE)));
    } finally {
      try {
        stringRedisTemplate.opsForZSet().remove(key, lease);
      } catch (Exception ignored) {
        /* 有界租约自动释放；不把已成功上传误报失败。 */
      }
    }
  }

  /** 解析新上传凭证或历史引用并检查材料归属。 */
  public String resolve(String owner, MaterialInput materialInput, String purpose) {
    if (materialInput == null) throw ApiFailure.invalid();
    if ("upload".equals(materialInput.kind())) {
      try {
        Receipt receipt =
            verificationMaterials.receipt(
                owner, purpose, "factory-material-v1", materialInput.uploadId());
        verificationMaterials.validateNewReference(receipt);
        return receipt.objectKey();
      } catch (ApiFailure apiFailure) {
        if (CODE_MATERIAL_REFERENCE_EXPIRED.equals(apiFailure.reason))
          throw conflict(apiFailure.reason);
        throw apiFailure;
      }
    }
    if (!"existing".equals(materialInput.kind()) || materialInput.locator() == null)
      throw ApiFailure.invalid();
    MaterialLocator materialLocator = materialInput.locator();
    if (!purpose(materialLocator.field()).equals(purpose)) throw invalid();
    FactorySource factorySource = FactorySource.from(materialLocator.sourceType());
    FactorySourceProjection factorySourceProjection =
        factoryAccess.owned(owner, factorySource, materialLocator.sourceId());
    return factorySnapshotData.key(
        factorySnapshotData.section(
            factorySource, factorySourceProjection, materialLocator.section()),
        materialLocator);
  }

  /** 构造材料引用无效的既定业务错误。 */
  private ApiFailure invalid() {
    return new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_REFERENCE_INVALID);
  }

  /** 按材料定位信息确定既定用途。 */
  public static String purpose(String field) {
    return switch (field) {
      case "factoryAvatar" -> "factory_avatar";
      case "businessLicense" -> "business_license";
      case "factoryPhotos" -> "factory_photo";
      case "caseImages" -> "service_case";
      default -> throw ApiFailure.invalid();
    };
  }

  /** 检查材料访问权限后返回短时签名。 */
  public MaterialView view(
      String actor, MaterialLocator materialLocator, boolean admin, String trace) {
    FactorySource factorySource = FactorySource.from(materialLocator.sourceType());
    com.mujian.backend.factory.projection.FactorySourceProjection factorySourceProjection;
    if (admin) {
      factoryAccess.admin(actor, factorySource.permission, "sensitive");
      factorySourceProjection =
          factoryReviewMapper.selectFactorySourceById(
              factorySource, id(materialLocator.sourceId()), false);
      if (factorySourceProjection == null) throw missing();
    } else
      factorySourceProjection =
          factoryAccess.owned(actor, factorySource, materialLocator.sourceId());
    String key =
        factorySnapshotData.key(
            factorySnapshotData.section(
                factorySource, factorySourceProjection, materialLocator.section()),
            materialLocator);
    Instant until = clock.instant().plusSeconds(120);
    factoryAudit.record(
        actor,
        factorySource.table,
        factorySourceProjection.getId(),
        "factory_material_view",
        admin ? "admin:" + factorySource.permission + ":sensitive" : null,
        trace,
        null,
        null,
        null,
        null,
        null,
        com.mujian.backend.common.support.D2Values.view(
            "field",
            materialLocator.field(),
            "section",
            materialLocator.section(),
            "imageIndex",
            materialLocator.imageIndex(),
            "caseIndex",
            materialLocator.caseIndex()));
    return new MaterialView(
        privateMaterialStore.sign(key, until), time(LocalDateTime.ofInstant(until, ZONE)));
  }
}
