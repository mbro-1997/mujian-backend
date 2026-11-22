package com.mujian.backend.account.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.account.vo.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.infrastructure.persistence.record.account.RuleRecord;
import java.time.*;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleAcceptanceService {
  public static final Set<String> CODES =
      Set.of(
          CODE_USER_SERVICE_AGREEMENT,
          CODE_PRIVACY_POLICY,
          CODE_RENTAL_TRANSACTION_RULE,
          CODE_DEPOSIT_COMPENSATION_RULE,
          CODE_LOGISTICS_TRANSPORT_RULE);
  private final LoginMapper loginMapper;
  private final Clock clock;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public RuleAcceptanceService(LoginMapper loginMapper, Clock clock) {
    this.loginMapper = loginMapper;
    this.clock = clock;
  }

  public record AcceptedRule(String documentId, String version) {}

  /** 读取当前生效记录并组装业务上下文。 */
  public List<RuleRecord> current() {
    List<RuleRecord> rows = loginMapper.selectRequiredRegistrationRulesForShare();
    Set<String> codes = new HashSet<>();
    boolean futureRule = false;
    for (RuleRecord ruleRecord : rows) {
      codes.add(ruleRecord.ruleCode());
      if (ruleRecord.effectiveAt().isAfter(now(clock))) futureRule = true;
    }
    if (rows.size() != 5 || !codes.equals(CODES) || futureRule)
      throw unavailable(CODE_RULE_DOCUMENT_UNAVAILABLE);
    return rows;
  }

  /** 核对已确认规则版本并在当前事务登记接受记录。 */
  public void accept(String user, List<AcceptedRule> input) {
    List<RuleRecord> rules = current();
    if (input == null || input.size() != 5) throw conflict(CODE_RULE_VERSION_CHANGED);
    Set<String> documentIds = new HashSet<>();
    for (AcceptedRule acceptedRule : input) documentIds.add(acceptedRule.documentId());
    if (documentIds.size() != 5) throw conflict(CODE_RULE_VERSION_CHANGED);
    for (RuleRecord ruleRecord : rules) {
      boolean matched = false;
      for (AcceptedRule acceptedRule : input) {
        if (ruleRecord.id().equals(acceptedRule.documentId())
            && ruleRecord.ruleVersion().equals(acceptedRule.version())) {
          matched = true;
          break;
        }
      }
      if (!matched) throw conflict(CODE_RULE_VERSION_CHANGED);
    }
    for (RuleRecord ruleRecord : rules)
      if (loginMapper.countAcceptedRegistrationRuleVersion(
              user, ruleRecord.id(), ruleRecord.ruleVersion(), ruleRecord.contentHash())
          == 0)
        loginMapper.insertRegistrationRuleAcceptance(Ids.next(), user, ruleRecord, now(clock));
  }

  /** 验证用户已接受当前要求的规则版本。 */
  public void requireAccepted(String user) {
    for (RuleRecord ruleRecord : current())
      if (loginMapper.countAcceptedRegistrationRuleVersion(
              user, ruleRecord.id(), ruleRecord.ruleVersion(), ruleRecord.contentHash())
          == 0) throw conflict(CODE_RULE_VERSION_CHANGED);
  }

  /** 转换规则摘要，不额外查询数据库。 */
  public RuleSummaryVO summary(RuleRecord ruleRecord) {
    return new RuleSummaryVO()
        .documentId(ruleRecord.id())
        .code(ruleRecord.ruleCode())
        .title(ruleRecord.ruleTitle())
        .version(ruleRecord.ruleVersion())
        .contentHash(ruleRecord.contentHash())
        .effectiveAt(time(ruleRecord.effectiveAt()));
  }

  /** 返回当前必须确认的注册规则。 */
  @Transactional(readOnly = true)
  public RuleCollectionVO collection() {
    List<RuleSummaryVO> rules = new ArrayList<>();
    for (RuleRecord ruleRecord : current()) rules.add(summary(ruleRecord));
    return new RuleCollectionVO(CODE_REGISTER, rules);
  }

  /** 读取指定已定义规则的正文。 */
  public RuleDocumentVO document(String id) {
    RuleRecord ruleRecord = loginMapper.selectRegistrationRuleById(id(id));
    if (ruleRecord == null
        || (!CODES.contains(ruleRecord.ruleCode())
            && !com.mujian.backend.common.enums.TradeRuleCode.rental_order_cancellation_refund_rule
                .name()
                .equals(ruleRecord.ruleCode()))) throw missing();
    if (com.mujian.backend.common.enums.TradeRuleCode.rental_order_cancellation_refund_rule
            .name()
            .equals(ruleRecord.ruleCode())
        && ruleRecord.effectiveAt().isAfter(now(clock))) throw missing();
    return new RuleDocumentVO()
        .documentId(ruleRecord.id())
        .code(ruleRecord.ruleCode())
        .title(ruleRecord.ruleTitle())
        .version(ruleRecord.ruleVersion())
        .contentHash(ruleRecord.contentHash())
        .effectiveAt(time(ruleRecord.effectiveAt()))
        .contentType(CODE_HTML)
        .contentText(ruleRecord.contentText())
        .status(ruleRecord.ruleStatus());
  }
}
