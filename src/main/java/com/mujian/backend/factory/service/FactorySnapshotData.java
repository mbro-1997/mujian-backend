package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.domain.FactoryChangeSnapshot;
import com.mujian.backend.factory.domain.FactoryFieldsDO;
import com.mujian.backend.factory.domain.FactorySnapshot.ServiceCase;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.CaseRead;
import com.mujian.backend.factory.dto.FactoryDtos.MaterialLocator;
import com.mujian.backend.factory.dto.FactoryDtos.OwnerFields;
import com.mujian.backend.factory.projection.*;
import java.util.*;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.*;
import tools.jackson.databind.JsonNode;

@Service
public class FactorySnapshotData {
  private final ObjectMapper objectMapper;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactorySnapshotData(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 从工厂查询结果还原正式快照字段。 */
  public FactorySnapshot read(FactorySourceProjection factorySourceProjection) {
    return new FactorySnapshot(
        factorySourceProjection.getFactoryName(),
        factorySourceProjection.getUnifiedSocialCreditCode(),
        factorySourceProjection.getFactoryAvatarUrl(),
        factorySourceProjection.getBusinessLicenseUrl(),
        Arrays.asList(
            objectMapper.readValue(factorySourceProjection.getFactoryPhotoUrls(), String[].class)),
        factorySourceProjection.getContactName(),
        factorySourceProjection.getContactPhone(),
        factorySourceProjection.getFactoryAddress(),
        factorySourceProjection.getMainBusiness(),
        factorySourceProjection.getWorkerCount(),
        Arrays.asList(
            objectMapper.readValue(
                factorySourceProjection.getSpecialtyCategoryIdsJson(), String[].class)),
        Arrays.asList(
            objectMapper.readValue(
                factorySourceProjection.getSpecialtyCategoryNamesSnapshotJson(), String[].class)),
        Arrays.asList(
            objectMapper.readValue(
                factorySourceProjection.getCaseMaterialsJson(), ServiceCase[].class)),
        factorySourceProjection.getRemark());
  }

  /** 读取或规范化工厂资料变更，保留冻结快照结构。 */
  public FactoryChangeSnapshot change(FactorySourceProjection factorySourceProjection) {
    return objectMapper.readValue(
        factorySourceProjection.getChangePayloadJson(), FactoryChangeSnapshot.class);
  }

  /** 将不可变业务快照按原JSON格式映射到持久化资料字段。 */
  public void fill(FactoryFieldsDO factoryFieldsDO, FactorySnapshot factorySnapshot) {
    factoryFieldsDO.setFactoryName(factorySnapshot.factory_name());
    factoryFieldsDO.setUnifiedSocialCreditCode(factorySnapshot.unified_social_credit_code());
    factoryFieldsDO.setFactoryAvatarUrl(factorySnapshot.factory_avatar_url());
    factoryFieldsDO.setBusinessLicenseUrl(factorySnapshot.business_license_url());
    factoryFieldsDO.setFactoryPhotoUrls(
        objectMapper.writeValueAsString(factorySnapshot.factory_photo_urls()));
    factoryFieldsDO.setContactName(factorySnapshot.contact_name());
    factoryFieldsDO.setContactPhone(factorySnapshot.contact_phone());
    factoryFieldsDO.setFactoryAddress(factorySnapshot.factory_address());
    factoryFieldsDO.setMainBusiness(factorySnapshot.main_business());
    factoryFieldsDO.setWorkerCount(factorySnapshot.worker_count());
    factoryFieldsDO.setSpecialtyCategoryIdsJson(
        objectMapper.writeValueAsString(factorySnapshot.specialty_category_ids_json()));
    factoryFieldsDO.setSpecialtyCategoryNamesSnapshotJson(
        objectMapper.writeValueAsString(factorySnapshot.specialty_category_names_snapshot_json()));
    factoryFieldsDO.setCaseMaterialsJson(
        objectMapper.writeValueAsString(factorySnapshot.case_materials_json()));
    factoryFieldsDO.setRemark(factorySnapshot.remark());
  }

  /** 按材料区段选择当前或冻结资料。 */
  public FactorySnapshot section(
      FactorySource factorySource,
      FactorySourceProjection factorySourceProjection,
      String section) {
    if (factorySource == FactorySource.CHANGE) {
      FactoryChangeSnapshot factoryChangeSnapshot = change(factorySourceProjection);
      return switch (section) {
        case "before" -> factoryChangeSnapshot.before();
        case "proposed" -> factoryChangeSnapshot.proposed();
        default -> throw com.mujian.backend.common.api.ApiFailure.invalid();
      };
    }
    if (!"source".equals(section)) throw com.mujian.backend.common.api.ApiFailure.invalid();
    return read(factorySourceProjection);
  }

  /** 构造材料定位信息，不暴露未授权对象路径。 */
  public MaterialLocator locator(
      FactorySource factorySource, String id, String section, String field, int image, Integer c) {
    return new MaterialLocator(factorySource.table, id, section, field, image, c);
  }

  /** 核对业务用户与材料归属。 */
  public OwnerFields owner(
      FactorySnapshot factorySnapshot, FactorySource factorySource, String id, String section) {
    ArrayList<CaseRead> cases = new ArrayList<CaseRead>();
    for (int c = 0; c < factorySnapshot.case_materials_json().size(); c++) {
      ServiceCase serviceCase = factorySnapshot.case_materials_json().get(c);
      ArrayList<MaterialLocator> imgs = new ArrayList<MaterialLocator>();
      for (int i = 0; i < serviceCase.image_object_keys().size(); i++)
        imgs.add(locator(factorySource, id, section, "caseImages", i, c));
      cases.add(new CaseRead(serviceCase.title(), serviceCase.description(), imgs));
    }
    ArrayList<MaterialLocator> photos = new ArrayList<MaterialLocator>();
    for (int i = 0; i < factorySnapshot.factory_photo_urls().size(); i++)
      photos.add(locator(factorySource, id, section, "factoryPhotos", i, null));
    return new OwnerFields(
        factorySnapshot.factory_name(),
        factorySnapshot.unified_social_credit_code(),
        locator(factorySource, id, section, "factoryAvatar", 0, null),
        locator(factorySource, id, section, "businessLicense", 0, null),
        photos,
        factorySnapshot.contact_name(),
        factorySnapshot.factory_address(),
        factorySnapshot.main_business(),
        factorySnapshot.worker_count(),
        factorySnapshot.specialty_category_ids_json(),
        cases,
        factorySnapshot.remark(),
        factorySnapshot.specialty_category_names_snapshot_json(),
        factorySnapshot.contact_phone());
  }

  /** 组装脱敏资料和材料定位信息。 */
  public MaskedFields masked(
      FactorySnapshot factorySnapshot, FactorySource factorySource, String id, String section) {
    OwnerFields ownerFields = owner(factorySnapshot, factorySource, id, section);
    return new MaskedFields(
        ownerFields.factoryName(),
        ownerFields.unifiedSocialCreditCode(),
        ownerFields.factoryAvatar(),
        ownerFields.businessLicense(),
        ownerFields.factoryPhotos(),
        ownerFields.factoryAddress(),
        ownerFields.mainBusiness(),
        ownerFields.workerCount(),
        ownerFields.specialtyCategoryIds(),
        ownerFields.cases(),
        ownerFields.remark(),
        ownerFields.specialtyCategoryNames(),
        factorySnapshot
                .contact_name()
                .substring(0, factorySnapshot.contact_name().offsetByCodePoints(0, 1))
            + "*",
        maskPhone(factorySnapshot.contact_phone()));
  }

  /** 生成或解析材料的稳定对象定位键。 */
  public String key(FactorySnapshot factorySnapshot, MaterialLocator materialLocator) {
    try {
      return switch (materialLocator.field()) {
        case "factoryAvatar" -> {
          if (materialLocator.imageIndex() != 0 || materialLocator.caseIndex() != null)
            throw new IllegalArgumentException();
          yield factorySnapshot.factory_avatar_url();
        }
        case "businessLicense" -> {
          if (materialLocator.imageIndex() != 0 || materialLocator.caseIndex() != null)
            throw new IllegalArgumentException();
          yield factorySnapshot.business_license_url();
        }
        case "factoryPhotos" -> {
          if (materialLocator.caseIndex() != null) throw new IllegalArgumentException();
          yield factorySnapshot.factory_photo_urls().get(materialLocator.imageIndex());
        }
        case "caseImages" ->
            factorySnapshot
                .case_materials_json()
                .get(materialLocator.caseIndex())
                .image_object_keys()
                .get(materialLocator.imageIndex());
        default -> throw new IllegalArgumentException();
      };
    } catch (Exception e) {
      throw new com.mujian.backend.common.api.ApiFailure(
          400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_REFERENCE_INVALID);
    }
  }

  /** 比较前后资料并返回排序后的变更字段。 */
  public List<String> changed(
      FactorySnapshot factorySnapshotBefore, FactorySnapshot factorySnapshotAfter) {
    JsonNode jsonNodeBefore = objectMapper.valueToTree(factorySnapshotBefore);
    JsonNode jsonNodeAfter = objectMapper.valueToTree(factorySnapshotAfter);
    Map<String, String> names =
        Map.ofEntries(
            Map.entry("factory_name", "factoryName"),
            Map.entry("unified_social_credit_code", "unifiedSocialCreditCode"),
            Map.entry("factory_avatar_url", "factoryAvatar"),
            Map.entry("business_license_url", "businessLicense"),
            Map.entry("factory_photo_urls", "factoryPhotos"),
            Map.entry("contact_name", "contactName"),
            Map.entry("contact_phone", "contactPhone"),
            Map.entry("factory_address", "factoryAddress"),
            Map.entry("main_business", "mainBusiness"),
            Map.entry("worker_count", "workerCount"),
            Map.entry("specialty_category_ids_json", "specialtyCategoryIds"),
            Map.entry("case_materials_json", "cases"),
            Map.entry("remark", "remark"));
    List<String> changedFields = new ArrayList<>();
    for (String field : names.keySet()) {
      if (!jsonNodeBefore.get(field).equals(jsonNodeAfter.get(field)))
        changedFields.add(names.get(field));
    }
    Collections.sort(changedFields);
    return List.copyOf(changedFields);
  }
}
