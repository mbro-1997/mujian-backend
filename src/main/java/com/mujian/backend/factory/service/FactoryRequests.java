package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.CaseInput;
import com.mujian.backend.factory.dto.FactoryDtos.ChangeFields;
import com.mujian.backend.factory.dto.FactoryDtos.OnboardingFields;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class FactoryRequests {
  private final ObjectMapper objectMapper;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryRequests(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 规范化可空文本，空白按空值处理。 */
  public static String optional(String s, int max) {
    return s == null || s.trim().isEmpty() ? null : text(s, max);
  }

  /** 校验并规范化非负版本号。 */
  public static String version(String v) {
    try {
      long n = Long.parseLong(v);
      if (n < 0) throw ApiFailure.invalid();
      return Long.toString(n);
    } catch (NumberFormatException e) {
      throw ApiFailure.invalid();
    }
  }

  /** 校验并规范化统一社会信用代码。 */
  public static String credit(String v) {
    v = text(v, 18).toUpperCase(Locale.ROOT);
    if (!v.matches("[0-9A-Z]{18}")) throw ApiFailure.invalid();
    return v;
  }

  /** 读取或校验工厂联系电话。 */
  public static String phone(String v) {
    v = text(v, 16);
    if (!v.matches("[+][1-9][0-9]{1,14}")) throw ApiFailure.invalid();
    return v;
  }

  /** 规范化工厂材料和服务案例，保持类别去重规则。 */
  public OnboardingFields fields(OnboardingFields onboardingFields) {
    if (onboardingFields.workerCount() == null || onboardingFields.workerCount() < 0)
      throw ApiFailure.invalid();
    SortedSet<String> categoryIds = new TreeSet<>();
    for (String categoryId : onboardingFields.specialtyCategoryIds())
      categoryIds.add(id(categoryId));
    List<String> categories = List.copyOf(categoryIds);
    if (categories.isEmpty()
        || categories.size() > 100
        || categories.size() != onboardingFields.specialtyCategoryIds().size())
      throw ApiFailure.invalid();
    List<CaseInput> cases = new ArrayList<>();
    for (CaseInput caseInput : onboardingFields.cases()) {
      String title = caseInput.title().trim(), description = caseInput.description().trim();
      if (title.isEmpty() && description.isEmpty() && caseInput.images().isEmpty())
        throw ApiFailure.invalid();
      cases.add(new CaseInput(title, description, caseInput.images()));
    }
    return new OnboardingFields(
        text(onboardingFields.factoryName(), 128),
        credit(onboardingFields.unifiedSocialCreditCode()),
        onboardingFields.factoryAvatar(),
        onboardingFields.businessLicense(),
        onboardingFields.factoryPhotos(),
        text(onboardingFields.contactName(), 64),
        text(onboardingFields.factoryAddress(), 255),
        optional(onboardingFields.mainBusiness(), 255),
        onboardingFields.workerCount(),
        categories,
        cases,
        optional(onboardingFields.remark(), 500));
  }

  /** 将入驻记录组装为既有申请响应。 */
  public OnboardingSubmit onboarding(OnboardingSubmit onboardingSubmit) {
    String previousApplicationId =
        onboardingSubmit.previousApplicationId() == null
            ? null
            : id(onboardingSubmit.previousApplicationId());
    List<AcceptedRule> acceptedRules = new ArrayList<>(onboardingSubmit.acceptedRules());
    acceptedRules.sort(Comparator.comparing(AcceptedRule::documentId));
    return new OnboardingSubmit(
        previousApplicationId, List.copyOf(acceptedRules), fields(onboardingSubmit.materials()));
  }

  /** 读取或规范化工厂资料变更，保留冻结快照结构。 */
  public ChangeSubmit change(ChangeSubmit changeSubmit) {
    ChangeFields changeFields = changeSubmit.proposed();
    OnboardingFields onboardingFields =
        fields(
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
                changeFields.remark()));
    return new ChangeSubmit(
        changeSubmit.previousApplicationId() == null
            ? null
            : id(changeSubmit.previousApplicationId()),
        version(changeSubmit.baseProfileVersion()),
        changeSubmit.phoneVerificationProof(),
        new ChangeFields(
            onboardingFields.factoryName(),
            onboardingFields.unifiedSocialCreditCode(),
            onboardingFields.factoryAvatar(),
            onboardingFields.businessLicense(),
            onboardingFields.factoryPhotos(),
            onboardingFields.contactName(),
            onboardingFields.factoryAddress(),
            onboardingFields.mainBusiness(),
            onboardingFields.workerCount(),
            onboardingFields.specialtyCategoryIds(),
            onboardingFields.cases(),
            onboardingFields.remark(),
            phone(changeFields.contactPhone())));
  }

  /** 按稳定字段顺序生成请求摘要用于幂等比较。 */
  public String hash(String operation, String target, Object body) {
    return sha(
        objectMapper.writeValueAsString(
            sorted(
                objectMapper.valueToTree(
                    view("operation", operation, "target", target, "body", body)))));
  }

  /** 递归规范化JSON键顺序，保留数组顺序及原字段。 */
  private JsonNode sorted(JsonNode jsonNode) {
    if (jsonNode.isObject()) {
      ObjectNode objectNode = objectMapper.createObjectNode();
      List<String> properties = new ArrayList<>(jsonNode.propertyNames());
      Collections.sort(properties);
      for (String property : properties) objectNode.set(property, sorted(jsonNode.get(property)));
      return objectNode;
    }
    if (jsonNode.isArray()) {
      ArrayNode arrayNode = objectMapper.createArrayNode();
      for (JsonNode jsonNodeValue : jsonNode) arrayNode.add(sorted(jsonNodeValue));
      return arrayNode;
    }
    return jsonNode;
  }

  /** 按UTF-8计算SHA-256摘要。 */
  public static String sha(String v) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
