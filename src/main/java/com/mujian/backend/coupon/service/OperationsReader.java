package com.mujian.backend.coupon.service;

import static com.mujian.backend.common.support.D2Values.missing;

import com.mujian.backend.common.api.*;
import com.mujian.backend.coupon.mapper.*;
import com.mujian.backend.coupon.projection.DistributionRows.Scope;
import com.mujian.backend.coupon.vo.OperationsViews.*;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class OperationsReader {
  private final OperationsMapper operationsMapper;
  private final DistributionMapper distributionMapper;
  private final ObjectMapper objectMapper;

  /** 使用固定模板版本及本页工厂范围。 */
  public OperationsReader(
      OperationsMapper operationsMapper,
      DistributionMapper distributionMapper,
      ObjectMapper objectMapper) {
    this.operationsMapper = operationsMapper;
    this.distributionMapper = distributionMapper;
    this.objectMapper = objectMapper;
  }

  /** 快照绑定模板和范围，客户端不能用业务版本号替代。 */
  private List<Template> templates(List<TemplateRow> rows) {
    Set<String> ids = new HashSet<>();
    for (TemplateRow templateRow : rows) if ("factory".equals(templateRow.applicableScopeType())) ids.add(templateRow.id());
    Map<String, List<String>> scopes = new HashMap<>();
    if (!ids.isEmpty())
      for (Scope scope : distributionMapper.selectScopes(ids)) {
        List<String> targets = scopes.get(scope.templateId());
        if (targets == null) {
          targets = new ArrayList<>();
          scopes.put(scope.templateId(), targets);
        }
        targets.add(scope.factoryId());
      }
    List<Template> result = new ArrayList<>();
    for (TemplateRow templateRowRow : rows) {
      List<String> factories = scopes.getOrDefault(templateRowRow.id(), List.of());
      String token;
      try {
        token =
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(objectMapper.writeValueAsBytes(List.of(templateRowRow, factories))));
      } catch (NoSuchAlgorithmException error) {
        throw new IllegalStateException(error);
      }
      result.add(
          new Template(
              templateRowRow.templateCode(),
              templateRowRow.templateName(),
              templateRowRow.couponType(),
              templateRowRow.faceAmountFen(),
              templateRowRow.discountRateBps(),
              templateRowRow.thresholdRentAmountFen(),
              templateRowRow.applicableScopeType(),
              factories,
              templateRowRow.validityType(),
              templateRowRow.validDays(),
              templateRowRow.validStartAt(),
              templateRowRow.validEndAt(),
              templateRowRow.id(),
              templateRowRow.templateVersion(),
              templateRowRow.status(),
              templateRowRow.issuedCount(),
              token));
    }
    return result;
  }

  /** 按固定 ID 返回领域记录。 */
  public Template readTemplate(String id, boolean lock) {
    TemplateRow templateRow = operationsMapper.selectTemplateById(id, lock);
    if (templateRow == null) throw missing();
    return templates(List.of(templateRow)).get(0);
  }

  /** 先取总数，越界不取明细。 */
  public PageResult<Template> pageTemplate(TemplateFilter filter, PageQuery page) {
    long total = operationsMapper.countTemplate(filter);
    List<TemplateRow> rows =
        total > page.offset() ? operationsMapper.selectTemplatePage(filter, page) : List.of();
    return PageResult.of(templates(rows), total, page);
  }

  /** 按固定 ID 返回领域记录。 */
  public Activity readActivity(String id, boolean lock) {
    Activity activity = operationsMapper.selectActivityById(id, lock);
    if (activity == null) throw missing();
    return activity;
  }

  /** 先取总数，越界不取明细。 */
  public PageResult<Activity> pageActivity(ActivityFilter filter, PageQuery page) {
    long total = operationsMapper.countActivity(filter);
    List<Activity> rows =
        total > page.offset() ? operationsMapper.selectActivityPage(filter, page) : List.of();
    return PageResult.of(rows, total, page);
  }

  /** 按固定 ID 返回领域记录。 */
  public Rule readRule(String id, boolean lock) {
    Rule operationsViewsRule = operationsMapper.selectRuleById(id, lock);
    if (operationsViewsRule == null) throw missing();
    return operationsViewsRule;
  }

  /** 先取总数，越界不取明细。 */
  public PageResult<Rule> pageRule(RuleFilter filter, PageQuery page) {
    long total = operationsMapper.countRule(filter);
    List<Rule> rows =
        total > page.offset() ? operationsMapper.selectRulePage(filter, page) : List.of();
    return PageResult.of(rows, total, page);
  }

  /** 先取总数，越界不取明细。 */
  public PageResult<Reward> pageReward(RewardFilter filter, PageQuery page) {
    long total = operationsMapper.countReward(filter);
    List<Reward> rows =
        total > page.offset() ? operationsMapper.selectRewardPage(filter, page) : List.of();
    return PageResult.of(rows, total, page);
  }

  /** 按固定 ID 返回领域记录。 */
  public Distribution readDistribution(String id, boolean lock) {
    Distribution distribution = operationsMapper.selectDistributionById(id, lock);
    if (distribution == null) throw missing();
    return distribution;
  }

  /** 先取总数，越界不取明细。 */
  public PageResult<Distribution> pageDistribution(DistributionFilter filter, PageQuery page) {
    long total = operationsMapper.countDistribution(filter);
    List<Distribution> rows =
        total > page.offset() ? operationsMapper.selectDistributionPage(filter, page) : List.of();
    return PageResult.of(rows, total, page);
  }

  /** 按固定 ID 返回领域记录。 */
  public Issued readIssued(String id, boolean lock) {
    Issued issued = operationsMapper.selectIssuedById(id, lock);
    if (issued == null) throw missing();
    return issued;
  }

  /** 从所选持券直接核对历次使用。 */
  public IssuedDetail issuedDetail(String id) {
    return new IssuedDetail(readIssued(id, false), operationsMapper.selectUsageIds(id));
  }

  /** 列表分页不逐行读取使用记录。 */
  public PageResult<Issued> pageIssued(IssuedFilter filter, PageQuery page) {
    long total = operationsMapper.countIssued(filter);
    List<Issued> rows =
        total > page.offset() ? operationsMapper.selectIssuedPage(filter, page) : List.of();
    return PageResult.of(rows, total, page);
  }
}
