package com.mujian.backend.custom.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.enums.TradingReason;

import com.mujian.backend.custom.dto.CustomForms;

import static com.mujian.backend.common.enums.TradingOperation.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.custom.dto.CustomForms.*;
import com.mujian.backend.custom.mapper.CustomRequirementMapper;
import com.mujian.backend.custom.mapper.CustomRequirementMapper.*;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class CustomRequirements {
  private final CustomRequirementMapper customRequirementMapper;
  private final TradingOperations tradingOperations;
  private final TradingAccess tradingAccess;
  private final FactoryAccess factoryAccess;
  private final ObjectMapper json;
  private final Clock clock;

  public CustomRequirements(
      CustomRequirementMapper customRequirementMapper,
      TradingOperations tradingOperations,
      TradingAccess tradingAccess,
      FactoryAccess factoryAccess,
      ObjectMapper json,
      Clock clock) {
    this.customRequirementMapper = customRequirementMapper;
    this.tradingOperations = tradingOperations;
    this.tradingAccess = tradingAccess;
    this.factoryAccess = factoryAccess;
    this.json = json;
    this.clock = clock;
  }

  public record Summary(
      String id,
      int version,
      String number,
      String title,
      String factoryId,
      String state,
      String updatedAt) {}

  public record PublishedQuote(
      String id,
      int quoteVersion,
      String state,
      QuoteTerms terms,
      int goodsFen,
      int totalFen,
      String validUntil) {}

  public record Detail(
      String id,
      int version,
      String number,
      String title,
      String factoryId,
      String state,
      String updatedAt,
      Draft form,
      List<PublishedQuote> quotes,
      String orderId,
      String responseDeadlineAt,
      String latestReason,
      List<String> allowedActions) {}

  /** 核对身份后读取指定工厂。 */
  public String factory(String actor) {
    if (!D2Codes.CODE_SUPPLIER.equals(factoryAccess.context(actor, false).roleCode()))
      throw ApiFailure.denied(TradingReason.SUPPLIER_ROLE_REQUIRED.name());
    return factoryAccess.profile(actor, false, false).getId();
  }

  /** 核对资源归属并按需锁定。 */
  public Requirement owned(String actor, boolean supplier, String id, boolean lock) {
    String factory = supplier ? factory(actor) : null;
    if (!supplier) tradingAccess.buyer(actor);
    Requirement requirement = customRequirementMapper.selectCustomRequirementById(id, lock);
    if (requirement == null
        || (supplier
            ? !factory.equals(requirement.factoryId()) || requirement.state().equals("draft")
            : !actor.equals(requirement.buyerId()))) throw missing();
    return requirement;
  }

  /** 按当前身份和筛选条件返回分页摘要。 */
  public PageResult<Summary> page(String actor, boolean supplier, String status, PageQuery page) {
    String factory = supplier ? factory(actor) : null;
    if (!supplier) tradingAccess.buyer(actor);
    List<CustomRequirements.Summary> list =
        customRequirementMapper.selectCustomRequirementPageByQuery(actor, factory, status, page).stream()
            .map(
                r ->
                    new Summary(
                        r.id(),
                        r.version(),
                        r.number(),
                        r.title(),
                        r.factoryId(),
                        r.state(),
                        time(r.updatedAt())))
            .toList();
    return PageResult.of(list, customRequirementMapper.selectCustomRequirementCountByQuery(actor, factory, status), page);
  }

  /** 组织编辑或提交所需的字段。 */
  Draft form(Requirement r) {
    Map<String, List<EvidenceRef>> byItem = new HashMap<>();
    for (CustomRequirementMapper.Attachment attachment : customRequirementMapper.selectAttachmentsByRequirement(r.id())) {
      List<EvidenceRef> attachments = byItem.get(attachment.itemId());
      if (attachments == null) {
        attachments = new ArrayList<>();
        byItem.put(attachment.itemId(), attachments);
      }
      attachments.add(new EvidenceRef(attachment.materialId(), attachment.name(), attachment.type()));
    }
    List<RequirementItem> items = new ArrayList<>();
    for (CustomRequirementMapper.Item customRequirementItem : customRequirementMapper.selectRequirementItemsByRequirementId(r.id()))
      items.add(
          new RequirementItem(
              customRequirementItem.id(),
              customRequirementItem.sourceSkuId(),
              customRequirementItem.title(),
              customRequirementItem.quantity(),
              customRequirementItem.dimension(),
              customRequirementItem.material(),
              customRequirementItem.style(),
              customRequirementItem.acceptanceNotes(),
              byItem.getOrDefault(customRequirementItem.id(), List.of())));
    return new Draft(
        null,
        r.factoryId(),
        r.title(),
        r.usageScene(),
        r.expectedCompletionDate(),
        r.deliveryCity(),
        r.deliveryMode(),
        r.remark(),
        r.budget() == null ? null : Arrays.asList(json.readValue(r.budget(), Integer[].class)),
        items,
        byItem.getOrDefault(null, List.of()));
  }

  /** 读取并核对需求对应的报价。 */
  PublishedQuote quote(Quote q) {
    String state =
        "published".equals(q.state()) && !now(clock).isBefore(q.validUntil())
            ? "expired"
            : q.state();
    return new PublishedQuote(
        q.id(),
        q.quoteVersion(),
        state,
        json.readValue(q.terms(), QuoteTerms.class),
        q.goodsFen(),
        q.totalFen(),
        time(q.validUntil()));
  }

  /** 组装当前身份有权查看的明细与操作。 */
  @Transactional(readOnly = true)
  public Detail detail(String actor, boolean supplier, String id) {
    CustomRequirementMapper.Requirement requirement = owned(actor, supplier, id, false);
    List<CustomRequirements.PublishedQuote> quotes = customRequirementMapper.selectQuotesByRequirement(id, supplier).stream().map(this::quote).toList();
    boolean expired =
        quotes.stream().anyMatch(q -> q.state().equals("expired"))
            && quotes.stream().noneMatch(q -> q.state().equals("published"));
    List<String> actions =
        supplier
            ? switch (requirement.state()) {
              case "submitted", "user_revision_requested" -> List.of("startCustomReview");
              case "factory_reviewing", "quoted" ->
                  quotes.stream().anyMatch(q -> q.state().equals("draft"))
                      ? List.of("saveCustomQuote", "publishCustomQuote")
                      : requirement.state().equals("quoted")
                          ? List.of("saveCustomQuote")
                          : List.of("declineCustomRequirement", "saveCustomQuote");
              default -> List.of();
            }
            : switch (requirement.state()) {
              case "draft" -> List.of("saveCustomDraft", "submitCustomRequirement");
              case "submitted" -> List.of("withdrawCustomRequirement");
              case "quoted" ->
                  expired
                      ? List.of("requestExpiredRequote", "withdrawCustomRequirement")
                      : List.of(
                          "requestQuoteRevision", "acceptCustomQuote", "withdrawCustomRequirement");
              default -> List.of();
            };
    if (supplier && !customRequirementMapper.activeFactory(requirement.factoryId())) actions = List.of();
    if (!supplier && !"active".equals(factoryAccess.context(actor, false).userStatus()))
      actions =
          actions.stream()
              .filter(
                  a ->
                      !Set.of("acceptCustomQuote", "submitCustomRequirement", "saveCustomDraft")
                          .contains(a))
              .toList();
    return new Detail(
        id,
        requirement.version(),
        requirement.number(),
        requirement.title(),
        requirement.factoryId(),
        requirement.state(),
        time(requirement.updatedAt()),
        form(requirement),
        quotes,
        customRequirementMapper.orderId(id),
        time(requirement.deadline()),
        requirement.reason(),
        actions);
  }

  /** 拒绝基于旧版本的写操作。 */
  public static void version(int actual, int expected) {
    if (actual != expected) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
  }

  /** 拒绝不满足业务前置条件的请求。 */
  public static void require(boolean valid) {
    if (!valid) throw conflict(BusinessFailureReason.CUSTOM_STATE_CHANGED.name());
  }

  /** 整理当前记录关联的明细集合。 */
  private static <T> List<T> list(List<T> source) {
    return source == null ? List.of() : source;
  }

  /** 核验进入下一阶段所需的完整字段。 */
  private static void complete(Draft customDraft) {
    if (customDraft.expectedCompletionDate() == null || customDraft.items().isEmpty()) throw ApiFailure.invalid();
    text(customDraft.title(), 128);
    text(customDraft.usageScene(), 255);
    text(customDraft.deliveryCity(), 64);
    text(customDraft.deliveryMode(), 32);
    text(customDraft.remark(), 1000);
    for (CustomForms.RequirementItem requirementItem : customDraft.items()) {
      text(requirementItem.title(), 128);
      text(requirementItem.dimension(), 500);
      text(requirementItem.material(), 500);
      text(requirementItem.style(), 500);
      text(requirementItem.acceptanceNotes(), 1000);
      if (requirementItem.quantity() == null
          || requirementItem.quantity() < 1
          || requirementItem.sourceSkuId() == null
              && requirementItem.attachments().stream().noneMatch(a -> "image".equals(a.type())))
        throw ApiFailure.invalid();
    }
  }

  /** 将已校验的材料关联到业务记录。 */
  private void attach(
      String root,
      String item,
      String actor,
      List<EvidenceRef> files,
      Set<String> used,
      Set<String> retained,
      LocalDateTime at) {
    for (CustomForms.EvidenceRef ref : list(files))
      if (!used.add(ref.materialId())
          || customRequirementMapper.insertCustomRequirementAttachment(
                  Ids.next(),
                  root,
                  item,
                  actor,
                  ref,
                  retained.contains(ref.materialId()),
                  used.size(),
                  at)
              != 1) throw ApiFailure.invalid();
  }

  /** 核对版本并保存本次提交。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt save(TradingCommand tradingCommand, String id, Draft customDraft) {
    tradingOperations.lock(tradingCommand.actor());
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    Requirement requirement = id == null ? null : owned(tradingCommand.actor(), false, id, true);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    factoryAccess.context(tradingCommand.actor(), true);
    if (!customRequirementMapper.activeFactory(customDraft.factoryId())) throw missing();
    LocalDateTime at = now(clock);
    List<Integer> b = customDraft.budgetRangeFen();
    if (b != null
        && (b.size() != 2
            || b.stream().allMatch(Objects::isNull)
            || b.stream().anyMatch(v -> v != null && v < 0)
            || b.get(0) != null && b.get(1) != null && b.get(0) > b.get(1)))
      throw ApiFailure.invalid();
    Set<String> prior = new HashSet<>(), retained = new HashSet<>();
    int next = 0;
    if (requirement == null) {
      id = Ids.next();
      customRequirementMapper.insertCustomRequirement(id, tradingCommand.actor(), tradingCommand.actor() + ":" + tradingCommand.requestId(), customDraft, at);
    } else {
      version(requirement.version(), customDraft.expectedVersion());
      require(requirement.state().equals("draft") && requirement.factoryId().equals(customDraft.factoryId()));
      for (CustomRequirementMapper.Attachment attachment : customRequirementMapper.selectAttachmentsByRequirement(id)) retained.add(attachment.materialId());
      for (CustomRequirementMapper.Item customRequirementItem : customRequirementMapper.selectRequirementItemsByRequirementId(id)) prior.add(customRequirementItem.id());
      customRequirementMapper.clearAttachments(id);
      customRequirementMapper.clearItems(id);
      customRequirementMapper.transitionRequirementState(id, "draft", null, at);
      next = requirement.version() + 1;
    }
    customRequirementMapper.updateCustomRequirement(id, customDraft, b == null ? null : json.writeValueAsString(b), at);
    Set<String> used = new HashSet<>();
    attach(id, null, tradingCommand.actor(), customDraft.attachments(), used, retained, at);
    int index = 0;
    for (CustomForms.RequirementItem requirementItem : list(customDraft.items())) {
      String item = requirementItem.id() == null ? Ids.next() : requirementItem.id();
      if (requirementItem.id() != null && !prior.remove(item)
          || requirementItem.sourceSkuId() != null && !customRequirementMapper.existsSkuForFactory(requirementItem.sourceSkuId(), customDraft.factoryId()))
        throw ApiFailure.invalid();
      customRequirementMapper.insertItem(item, id, requirementItem, index++, at);
      attach(id, item, tradingCommand.actor(), requirementItem.attachments(), used, retained, at);
    }
    return tradingOperations.record(tradingCommand, "custom_requirement", id, next);
  }

  /** 核验请求并记录可回放的执行结果。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt command(TradingCommand tradingCommand, String id, int expected, String reason) {
    tradingOperations.lock(tradingCommand.actor());
    CustomRequirementMapper.Requirement requirement = owned(tradingCommand.actor(), D2Codes.CODE_SUPPLIER.equals(tradingCommand.operation().role()), id, true);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    version(requirement.version(), expected);
    LocalDateTime at = now(clock);
    String next;
    switch (tradingCommand.operation()) {
      case submitCustomRequirement -> {
        require(requirement.state().equals("draft"));
        factoryAccess.context(tradingCommand.actor(), true);
        if (!customRequirementMapper.activeFactory(requirement.factoryId())) throw missing();
        complete(form(requirement));
        next = "submitted";
      }
      case startCustomReview -> {
        require(customRequirementMapper.activeFactory(requirement.factoryId()));
        require(Set.of("submitted", "user_revision_requested").contains(requirement.state()));
        next = "factory_reviewing";
      }
      case declineCustomRequirement -> {
        require(requirement.state().equals("factory_reviewing"));
        next = "factory_declined";
      }
      case withdrawCustomRequirement -> {
        require(Set.of("submitted", "quoted").contains(requirement.state()));
        customRequirementMapper.retireCustomQuote(id, "rejected", at);
        next = "withdrawn";
      }
      case requestExpiredRequote, requestQuoteRevision -> {
        require(requirement.state().equals("quoted"));
        boolean expired = tradingCommand.operation() == requestExpiredRequote;
        Quote published =
            customRequirementMapper.selectQuotesByRequirement(id, false).stream()
                .filter(q -> "published".equals(q.state()))
                .findFirst()
                .orElseThrow(() -> conflict(BusinessFailureReason.CUSTOM_STATE_CHANGED.name()));
        require(expired == !at.isBefore(published.validUntil()));
        customRequirementMapper.retireCustomQuote(id, expired ? "expired" : "superseded", at);
        next = "user_revision_requested";
      }
      default -> throw ApiFailure.invalid();
    }
    customRequirementMapper.transitionRequirementState(id, next, reason, at);
    return tradingOperations.record(tradingCommand, "custom_requirement", id, expected + 1);
  }

  /** 校验需求与报价版本并执行对应命令。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt quoteCommand(
      TradingCommand tradingCommand, String id, int expected, QuoteTerms quoteTerms, String quoteId) {
    tradingOperations.lock(tradingCommand.actor());
    CustomRequirementMapper.Requirement requirement = owned(tradingCommand.actor(), true, id, true);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    require(customRequirementMapper.activeFactory(requirement.factoryId()));
    version(requirement.version(), expected);
    require(Set.of("factory_reviewing", "quoted").contains(requirement.state()));
    LocalDateTime at = now(clock);
    List<CustomRequirementMapper.Quote> quotes = customRequirementMapper.selectQuotesByRequirement(id, true);
    CustomRequirementMapper.Quote latest = quotes.isEmpty() ? null : quotes.get(0);
    String state = requirement.state();
    if (quoteTerms == null) {
      require(latest != null && latest.state().equals("draft") && latest.id().equals(quoteId));
      customRequirementMapper.retireCustomQuote(id, "superseded", at);
      customRequirementMapper.publishCustomQuote(quoteId, at);
      state = "quoted";
    } else {
      if (quoteTerms.deliveryMode().equals("buyer_pickup") && quoteTerms.freightFen() != 0)
        throw ApiFailure.invalid();
      HashMap<String,Integer> source = new HashMap<String, Integer>();
      for (CustomRequirementMapper.Item customRequirementItem : customRequirementMapper.selectRequirementItemsByRequirementId(id)) source.put(customRequirementItem.id(), customRequirementItem.quantity());
      long goods = 0;
      for (CustomForms.QuotedItem quotedItem : quoteTerms.items()) {
        if (!Objects.equals(source.remove(quotedItem.itemId()), quotedItem.quantity())) throw ApiFailure.invalid();
        goods += (long) quotedItem.unitPriceFen() * quotedItem.quantity();
        if (goods + quoteTerms.freightFen() > Integer.MAX_VALUE) throw ApiFailure.invalid();
      }
      if (!source.isEmpty() || goods + quoteTerms.freightFen() == 0) throw ApiFailure.invalid();
      boolean draft = latest != null && latest.state().equals("draft");
      customRequirementMapper.saveQuote(
          draft ? latest.id() : Ids.next(),
          id,
          requirement.factoryId(),
          draft ? latest.quoteVersion() : quotes.size() + 1,
          json.writeValueAsString(quoteTerms),
          quoteTerms,
          (int) goods,
          (int) goods + quoteTerms.freightFen(),
          at);
    }
    customRequirementMapper.transitionRequirementState(id, state, null, at);
    return tradingOperations.record(tradingCommand, "custom_requirement", id, expected + 1);
  }
}
