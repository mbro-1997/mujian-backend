package com.mujian.backend.prop.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.PropReviewSources.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.PropReasonCode;
import com.mujian.backend.factory.dto.FactoryDtos.UploadResult;
import com.mujian.backend.factory.service.FactoryMaterials;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.ImageContent;
import com.mujian.backend.material.service.VerificationMaterials;
import com.mujian.backend.material.service.VerificationMaterials.Receipt;
import com.mujian.backend.prop.domain.PropImageDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.dto.ImageInputDTO;
import com.mujian.backend.prop.dto.ImageLocatorDTO;
import com.mujian.backend.prop.mapper.PropImageMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.projection.PropReviewProjection;
import com.mujian.backend.prop.service.PropAudit;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.utils.PropJson;
import com.mujian.backend.prop.vo.ImageLocatorVO;
import com.mujian.backend.prop.vo.ImageReadVO;
import com.mujian.backend.prop.vo.ImageViewVO;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import com.mujian.backend.prop.vo.UploadResultVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.Instant;
import java.util.*;
import java.util.ArrayList;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class PropMaterialServiceImpl implements PropMaterialService {
  private final PropAccess propAccess;
  private final VerificationMaterials verificationMaterials;
  private final FactoryMaterials factoryMaterials;
  private final PropImageMapper propImageMapper;
  private final PrivateMaterialStore privateMaterialStore;
  private final PropJson propJson;
  private final Clock clock;
  private final PropAudit propAudit;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropMaterialServiceImpl(
      PropAccess propAccess,
      VerificationMaterials verificationMaterials,
      FactoryMaterials factoryMaterials,
      PropImageMapper propImageMapper,
      PrivateMaterialStore privateMaterialStore,
      PropJson propJson,
      Clock clock,
      PropAudit propAudit) {
    this.propAccess = propAccess;
    this.verificationMaterials = verificationMaterials;
    this.factoryMaterials = factoryMaterials;
    this.propImageMapper = propImageMapper;
    this.privateMaterialStore = privateMaterialStore;
    this.propJson = propJson;
    this.clock = clock;
    this.propAudit = propAudit;
  }

  /** 校验上传用途后复用现有材料上传能力。 */
  public UploadResultVO upload(String actor, String purpose, byte[] byteArray, boolean admin) {
    if (!(admin ? "qr_exception_evidence" : "prop_image").equals(purpose))
      throw ApiFailure.invalid();
    UploadResult uploadResult = factoryMaterials.uploadProp(actor, purpose, byteArray);
    return new UploadResultVO()
        .uploadId(uploadResult.uploadId())
        .purpose(purpose)
        .expiresAt(uploadResult.expiresAt());
  }

  /** 在进入业务事务前验证上传凭证和材料归属。 */
  public void preflight(String actor, List<ImageInputDTO> imageInputDTOList, String purpose) {
    for (ImageInputDTO imageInputDTO : imageInputDTOList)
      if ("upload".equals(imageInputDTO.kind())) {
        try {
          verificationMaterials.receipt(
              actor,
              purpose,
              VerificationMaterials.domain(purpose) + "-material-v1",
              imageInputDTO.uploadId());
        } catch (ApiFailure apiFailure) {
          if (CODE_MATERIAL_REFERENCE_EXPIRED.equals(apiFailure.reason))
            throw conflict(apiFailure.reason);
          throw apiFailure;
        }
      }
  }

  /** 验证上传或历史材料引用，取得可保存的对象路径。 */
  public String resolve(String actor, ImageInputDTO imageInputDTO, String purpose) {
    if ("upload".equals(imageInputDTO.kind())) {
      try {
        Receipt receipt =
            verificationMaterials.localReceipt(
                actor,
                purpose,
                VerificationMaterials.domain(purpose) + "-material-v1",
                imageInputDTO.uploadId());
        verificationMaterials.validateNewReference(receipt);
        return receipt.objectKey();
      } catch (ApiFailure apiFailure) {
        if (CODE_MATERIAL_REFERENCE_EXPIRED.equals(apiFailure.reason))
          throw conflict(apiFailure.reason);
        throw apiFailure;
      }
    }
    if (!"existing".equals(imageInputDTO.kind()) || imageInputDTO.locator() == null)
      throw ApiFailure.invalid();
    if (("qr_exception_evidence".equals(purpose))
        != imageInputDTO.locator().sourceType().equals(QR_EXCEPTION))
      throw new ApiFailure(
          400, CODE_INVALID_ARGUMENT, PropReasonCode.MATERIAL_REFERENCE_INVALID.name());
    return key(actor, imageInputDTO.locator(), "qr_exception_evidence".equals(purpose));
  }

  /** 按来源、快照区段和序号校验材料归属与查看权限。 */
  @SuppressWarnings("unchecked")
  public String key(String actor, ImageLocatorDTO imageLocatorDTO, boolean admin) {
    String domain =
        switch (imageLocatorDTO.sourceType()) {
          case "sku" -> "prop-asset";
          case SKU_AUDIT -> "prop-review";
          case QR_EXCEPTION -> "prop-qr";
          default -> throw ApiFailure.invalid();
        };
    if (admin) propAccess.admin(actor, domain, "sensitive");
    else if (imageLocatorDTO.sourceType().equals(QR_EXCEPTION)) throw missing();
    List<String> keys;
    if (imageLocatorDTO.sourceType().equals("sku")) {
      PropSkuDO propSkuDO = propAccess.sku(id(imageLocatorDTO.sourceId()));
      if (!admin) propAccess.own(actor, propSkuDO.getId());
      if (!imageLocatorDTO.section().equals("current")) throw ApiFailure.invalid();
      keys = new ArrayList<>();
      for (PropImageDO propImageDO : propImageMapper.selectPropImageListBySkuId(propSkuDO.getId()))
        keys.add(propImageDO.getImageUrl());
    } else {
      PropReviewProjection propReviewProjection =
          propAccess.review(imageLocatorDTO.sourceType(), id(imageLocatorDTO.sourceId()), false);
      if (propReviewProjection == null) throw missing();
      if (!admin) propAccess.own(actor, propReviewProjection.getSkuId());
      if (imageLocatorDTO.sourceType().equals(QR_EXCEPTION)) {
        if (!imageLocatorDTO.section().equals("evidence")) throw ApiFailure.invalid();
        keys = propJson.strings(propReviewProjection.getEvidenceUrlsJson());
      } else if (imageLocatorDTO.section().equals("current")) {
        keys = new ArrayList<>();
        for (PropImageDO propImageDO :
            propImageMapper.selectPropImageListBySkuId(propReviewProjection.getSkuId()))
          keys.add(propImageDO.getImageUrl());
      } else {
        if (!Set.of("before", "proposed").contains(imageLocatorDTO.section()))
          throw ApiFailure.invalid();
        String raw =
            imageLocatorDTO.section().equals("before")
                ? propReviewProjection.getBeforeSnapshotJson()
                : propReviewProjection.getProposedSnapshotJson();
        if (raw == null) throw missing();
        keys =
            propJson
                .objectMapper
                .readValue(raw, com.mujian.backend.prop.dto.PropSkuSnapshotDTO.class)
                .fields()
                .getImageKeys();
      }
    }
    if (imageLocatorDTO.index() == null
        || imageLocatorDTO.index() < 0
        || imageLocatorDTO.index() >= keys.size()) throw missing();
    return keys.get(imageLocatorDTO.index());
  }

  /** 为对象路径签发既定期限的图片访问链接。 */
  public ImageViewVO sign(String key) {
    Instant until = clock.instant().plusSeconds(120);
    return new ImageViewVO()
        .url(privateMaterialStore.sign(key, until))
        .expiresAt(time(LocalDateTime.ofInstant(until, ZONE)));
  }

  /** 签发可选展示图片，存储暂不可用时按原规则返回空值。 */
  public ImageViewVO optional(String key) {
    if (key == null) return null;
    try {
      return sign(key);
    } catch (ApiFailure apiFailure) {
      if (apiFailure.reason.equals(CODE_OBJECT_STORAGE_UNAVAILABLE)) return null;
      throw apiFailure;
    }
  }

  /** 为已公开道具签发统一到期时间的链接；不改变私有材料策略。 */
  public PublicImageViewVO publicImage(String key) {
    if (key == null) return null;
    Instant until =
        clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1800);
    try {
      String cacheKey =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(key.getBytes(StandardCharsets.UTF_8)));
      return new PublicImageViewVO()
          .url(privateMaterialStore.sign(key, until))
          .expiresAt(time(LocalDateTime.ofInstant(until, ZONE)))
          .cacheKey(cacheKey);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK缺少SHA-256", exception);
    } catch (ApiFailure apiFailure) {
      if (apiFailure.reason.equals(CODE_OBJECT_STORAGE_UNAVAILABLE)) return null;
      throw apiFailure;
    }
  }

  /** 验证私有材料权限并记录查看审计，再签发短时链接。 */
  public ImageViewVO viewImage(
      String actor, ImageLocatorDTO imageLocatorDTO, boolean admin, String trace) {
    String key = key(actor, imageLocatorDTO, admin);
    if (admin) auditAdminImageView(actor, imageLocatorDTO, trace);
    return sign(key);
  }

  /** 使用原材料定位、权限和查看审计，随后从存储读取图片。 */
  @Override
  public ImageContent readImage(String actor, ImageLocatorDTO imageLocatorDTO, boolean admin, String trace) {
    String key = key(actor, imageLocatorDTO, admin);
    if (admin) auditAdminImageView(actor, imageLocatorDTO, trace);
    return privateMaterialStore.readImage(key);
  }

  /** 统一记录签名查看和图片内容读取的授权审计。 */
  private void auditAdminImageView(String actor, ImageLocatorDTO imageLocatorDTO, String trace) {
    propAudit.record(
        actor,
        imageLocatorDTO.sourceType(),
        imageLocatorDTO.sourceId(),
        "prop_material_view",
        "admin:"
            + (imageLocatorDTO.sourceType().equals("sku")
                ? "prop-asset"
                : imageLocatorDTO.sourceType().equals(SKU_AUDIT) ? "prop-review" : "prop-qr")
            + ":sensitive",
        trace,
        null,
        null,
        null,
        null,
        null,
        imageLocatorDTO);
  }

  /** 按原顺序组装材料定位信息及获授权的预览链接。 */
  public List<ImageReadVO> images(
      List<String> keys, String source, String id, String section, boolean preview) {
    ArrayList<ImageReadVO> imageReadVOList = new ArrayList<ImageReadVO>();
    for (int i = 0; i < keys.size(); i++)
      imageReadVOList.add(
          new ImageReadVO()
              .locator(
                  new ImageLocatorVO().sourceType(source).sourceId(id).section(section).index(i))
              .preview(preview ? optional(keys.get(i)) : null));
    return imageReadVOList;
  }

  /** 检查所有已保存的对象路径仍可被存储服务访问。 */
  public void verifyStored(List<String> keys) {
    for (String key : keys) privateMaterialStore.head(key);
  }
}
