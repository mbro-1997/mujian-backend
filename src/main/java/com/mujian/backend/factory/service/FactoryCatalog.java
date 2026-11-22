package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.PlatformRuleDocumentMapper;
import com.mujian.backend.account.mapper.UserRuleAcceptanceMapper;
import com.mujian.backend.common.dictionary.mapper.OptionDictionaryMapper;
import com.mujian.backend.common.dictionary.projection.DictionaryRecordProjection;
import com.mujian.backend.common.enums.DictionaryGroup;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.factory.domain.FactorySnapshot;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.FactoryRule;
import com.mujian.backend.factory.mapper.FactoryProfileMapper;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.projection.*;
import java.time.*;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FactoryCatalog {
  public static final Set<String> RULES =
      Set.of("factory_onboarding_fulfillment_rule", "factory_logistics_outbound_rule");
  private final FactoryProfileMapper factoryProfileMapper;
  private final FactoryReviewMapper factoryReviewMapper;
  private final OptionDictionaryMapper optionDictionaryMapper;
  private final PlatformRuleDocumentMapper platformRuleDocumentMapper;
  private final UserRuleAcceptanceMapper userRuleAcceptanceMapper;
  private final Clock clock;
  private final FactoryAccess factoryAccess;
  private final FactorySnapshotData factorySnapshotData;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryCatalog(
      FactoryProfileMapper factoryProfileMapper,
      FactoryReviewMapper factoryReviewMapper,
      OptionDictionaryMapper optionDictionaryMapper,
      PlatformRuleDocumentMapper platformRuleDocumentMapper,
      UserRuleAcceptanceMapper userRuleAcceptanceMapper,
      Clock clock,
      FactoryAccess factoryAccess,
      FactorySnapshotData factorySnapshotData) {
    this.factoryProfileMapper = factoryProfileMapper;
    this.factoryReviewMapper = factoryReviewMapper;
    this.optionDictionaryMapper = optionDictionaryMapper;
    this.platformRuleDocumentMapper = platformRuleDocumentMapper;
    this.userRuleAcceptanceMapper = userRuleAcceptanceMapper;
    this.clock = clock;
    this.factoryAccess = factoryAccess;
    this.factorySnapshotData = factorySnapshotData;
  }

  /** 返回当前资料可选的字典项并保留已有选择。 */
  public Options options(String user) {
    factoryAccess.context(user, false);
    FactorySourceProjection factorySourceProjection =
        factoryProfileMapper.selectFactoryProfileByOwner(user, false);
    FactorySourceProjection factorySourceProjectionApplication =
        factoryReviewMapper.selectCurrentFactoryAuditByOwner(
            com.mujian.backend.common.enums.FactorySource.ONBOARDING, user);
    FactorySnapshot factorySnapshot =
        factorySourceProjection != null
            ? factorySnapshotData.read(factorySourceProjection)
            : factorySourceProjectionApplication != null
                ? factorySnapshotData.read(factorySourceProjectionApplication)
                : null;
    List<String> retained =
        factorySnapshot == null ? List.<String>of() : factorySnapshot.specialty_category_ids_json();
    List<Option> options = new ArrayList<>();
    for (DictionaryRecordProjection dictionaryRecordProjection :
        optionDictionaryMapper.selectDictionaryRecordsByGroup(
            DictionaryGroup.PROP_SKU_TYPE.code())) {
      options.add(
          new Option(
              dictionaryRecordProjection.getId(),
              dictionaryRecordProjection.getOptionCode(),
              dictionaryRecordProjection.getOptionLabel(),
              dictionaryRecordProjection.getEnabled() == 1,
              retained.contains(dictionaryRecordProjection.getId())));
    }
    return new Options(DictionaryGroup.PROP_SKU_TYPE.code(), options);
  }

  /** 批量读取并冻结所选字典名称，不逐项访问数据库。 */
  public List<String> names(List<String> ids, FactorySnapshot factorySnapshotOld) {
    List<DictionaryRecordProjection> options =
        optionDictionaryMapper.selectPropTypeOptionsByIdsForShare(ids);
    if (options.size() != ids.size()) throw com.mujian.backend.common.api.ApiFailure.invalid();
    ArrayList<String> names = new ArrayList<String>();
    Map<String, DictionaryRecordProjection> optionById = new HashMap<>();
    for (DictionaryRecordProjection dictionaryRecordProjection : options)
      optionById.put(dictionaryRecordProjection.getId(), dictionaryRecordProjection);
    for (String id : ids) {
      DictionaryRecordProjection dictionaryRecordProjectionSelected = optionById.get(id);
      if (dictionaryRecordProjectionSelected == null) throw new NoSuchElementException();
      int index =
          factorySnapshotOld == null
              ? -1
              : factorySnapshotOld.specialty_category_ids_json().indexOf(id);
      if (dictionaryRecordProjectionSelected.getEnabled() == 0 && index < 0)
        throw com.mujian.backend.common.api.ApiFailure.invalid();
      names.add(
          index >= 0
              ? factorySnapshotOld.specialty_category_names_snapshot_json().get(index)
              : dictionaryRecordProjectionSelected.getOptionLabel());
    }
    return names;
  }

  /** 读取当前生效记录并组装业务上下文。 */
  private List<FactoryRuleProjection> current() {
    List<FactoryRuleProjection> rows =
        platformRuleDocumentMapper.selectRequiredSupplierRulesForShare(RULES);
    if (rows.size() != 2) throw unavailable(CODE_RULE_DOCUMENT_UNAVAILABLE);
    Set<String> codes = new HashSet<>();
    for (FactoryRuleProjection factoryRuleProjection : rows)
      codes.add(factoryRuleProjection.getRuleCode());
    if (!codes.equals(RULES)) throw unavailable(CODE_RULE_DOCUMENT_UNAVAILABLE);
    for (FactoryRuleProjection factoryRuleProjection : rows) {
      if (factoryRuleProjection.getEffectiveAt().isAfter(now(clock)))
        throw unavailable(CODE_RULE_DOCUMENT_UNAVAILABLE);
    }
    return rows;
  }

  /** 组装公开摘要，保持脱敏与字段契约。 */
  private FactoryRule summary(FactoryRuleProjection factoryRuleProjection) {
    return new FactoryRule(
        factoryRuleProjection.getId(),
        factoryRuleProjection.getRuleCode(),
        factoryRuleProjection.getRuleTitle(),
        factoryRuleProjection.getRuleVersion(),
        factoryRuleProjection.getContentHash(),
        time(factoryRuleProjection.getEffectiveAt()));
  }

  /** 返回当前生效的业务规则版本。 */
  public FactoryRules rules(String user) {
    factoryAccess.context(user, false);
    List<FactoryRule> rules = new ArrayList<>();
    for (FactoryRuleProjection factoryRuleProjection : current())
      rules.add(summary(factoryRuleProjection));
    return new FactoryRules("supplier_apply", rules);
  }

  /** 校验规则文档是否有效后返回内容。 */
  public FactoryRuleDocument document(String user, String id) {
    factoryAccess.context(user, false);
    List<FactoryRuleProjection> rows =
        platformRuleDocumentMapper.selectSupplierRuleById(id(id), RULES, now(clock));
    if (rows.isEmpty()) throw missing();
    FactoryRuleProjection factoryRuleProjection = rows.get(0);
    FactoryRule factoryRule = summary(factoryRuleProjection);
    return new FactoryRuleDocument(
        factoryRule.documentId(),
        factoryRule.code(),
        factoryRule.title(),
        factoryRule.version(),
        factoryRule.contentHash(),
        factoryRule.effectiveAt(),
        "html",
        factoryRuleProjection.getContentText(),
        factoryRuleProjection.getRuleStatus());
  }

  /** 核对已确认规则版本并在当前事务登记接受记录。 */
  public List<String> accept(String owner, List<AcceptedRule> accepted) {
    List<FactoryRuleProjection> rules = current();
    if (accepted.size() != 2) throw conflict(CODE_RULE_VERSION_CHANGED);
    Set<String> documentIds = new HashSet<>();
    for (AcceptedRule acceptedRule : accepted) documentIds.add(acceptedRule.documentId());
    if (documentIds.size() != 2) throw conflict(CODE_RULE_VERSION_CHANGED);
    for (FactoryRuleProjection factoryRuleProjection : rules) {
      boolean matched = false;
      for (AcceptedRule acceptedRule : accepted) {
        if (acceptedRule.documentId().equals(factoryRuleProjection.getId())
            && acceptedRule.version().equals(factoryRuleProjection.getRuleVersion())
            && acceptedRule.contentHash().equals(factoryRuleProjection.getContentHash())) {
          matched = true;
          break;
        }
      }
      if (!matched) throw conflict(CODE_RULE_VERSION_CHANGED);
      if (userRuleAcceptanceMapper.countSupplierRuleAcceptance(owner, factoryRuleProjection.getId())
          == 0)
        userRuleAcceptanceMapper.insertSupplierRuleAcceptance(
            Ids.next(),
            owner,
            factoryRuleProjection.getId(),
            factoryRuleProjection.getRuleCode(),
            factoryRuleProjection.getRuleVersion(),
            factoryRuleProjection.getContentHash(),
            now(clock));
    }
    List<String> ruleIds = new ArrayList<>();
    for (FactoryRuleProjection factoryRuleProjection : rules)
      ruleIds.add(factoryRuleProjection.getId());
    Collections.sort(ruleIds);
    return List.copyOf(ruleIds);
  }

  /** 批量读取已接受规则版本，保留原版本内容。 */
  public List<AcceptedRule> accepted(List<String> ids) {
    List<AcceptedRule> acceptedRules = new ArrayList<>();
    for (FactoryRuleProjection factoryRuleProjection :
        platformRuleDocumentMapper.selectAcceptedRulesByIds(ids)) {
      acceptedRules.add(
          new AcceptedRule(
              factoryRuleProjection.getId(),
              factoryRuleProjection.getRuleVersion(),
              factoryRuleProjection.getContentHash()));
    }
    return List.copyOf(acceptedRules);
  }
}
