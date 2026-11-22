package com.mujian.backend.factory.service;

import com.mujian.backend.factory.domain.FactorySnapshot;
import com.mujian.backend.factory.domain.FactorySnapshot.ServiceCase;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FactorySnapshotBuilder {
  private final FactoryMaterials factoryMaterials;
  private final FactoryCatalog factoryCatalog;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactorySnapshotBuilder(FactoryMaterials factoryMaterials, FactoryCatalog factoryCatalog) {
    this.factoryMaterials = factoryMaterials;
    this.factoryCatalog = factoryCatalog;
  }

  /** 解析已授权材料并构造本次工厂资料快照。 */
  public FactorySnapshot build(
      String owner,
      OnboardingFields onboardingFields,
      String phone,
      FactorySnapshot factorySnapshot) {
    List<ServiceCase> cases = new ArrayList<>();
    for (CaseInput caseInput : onboardingFields.cases()) {
      List<String> imageKeys = new ArrayList<>();
      for (MaterialInput materialInput : caseInput.images())
        imageKeys.add(factoryMaterials.resolve(owner, materialInput, "service_case"));
      cases.add(
          new ServiceCase(caseInput.title(), caseInput.description(), List.copyOf(imageKeys)));
    }
    String avatarKey =
        factoryMaterials.resolve(owner, onboardingFields.factoryAvatar(), "factory_avatar");
    String licenseKey =
        factoryMaterials.resolve(owner, onboardingFields.businessLicense(), "business_license");
    List<String> photoKeys = new ArrayList<>();
    for (MaterialInput materialInput : onboardingFields.factoryPhotos())
      photoKeys.add(factoryMaterials.resolve(owner, materialInput, "factory_photo"));
    return new FactorySnapshot(
        onboardingFields.factoryName(),
        onboardingFields.unifiedSocialCreditCode(),
        avatarKey,
        licenseKey,
        List.copyOf(photoKeys),
        onboardingFields.contactName(),
        phone,
        onboardingFields.factoryAddress(),
        onboardingFields.mainBusiness(),
        onboardingFields.workerCount(),
        onboardingFields.specialtyCategoryIds(),
        factoryCatalog.names(onboardingFields.specialtyCategoryIds(), factorySnapshot),
        cases,
        onboardingFields.remark());
  }

  /** 解析已授权材料并构造本次工厂资料快照。 */
  public FactorySnapshot build(
      String owner, ChangeFields changeFields, FactorySnapshot factorySnapshotOld) {
    return build(
        owner,
        new OnboardingFields(
            changeFields.factoryName(),
            changeFields.unifiedSocialCreditCode(),
            changeFields.factoryAvatar(),
            changeFields.businessLicense(),
            changeFields.factoryPhotos(),
            changeFields.contactName(),
            changeFields.factoryAddress(),
            changeFields.mainBusiness(),
            changeFields.workerCount(),
            changeFields.specialtyCategoryIds(),
            changeFields.cases(),
            changeFields.remark()),
        changeFields.contactPhone(),
        factorySnapshotOld);
  }
}
