package com.mujian.backend.custom.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.custom.dto.CustomForms.*;
import java.time.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CustomRequirementMapper {

  record Requirement(
      String id,
      String buyerId,
      String factoryId,
      String state,
      int version,
      String number,
      String title,
      String usageScene,
      LocalDate expectedCompletionDate,
      String deliveryCity,
      String deliveryMode,
      String remark,
      String budget,
      LocalDateTime deadline,
      String reason,
      LocalDateTime updatedAt) {}

  record Item(
      String id,
      String sourceSkuId,
      String title,
      Integer quantity,
      String dimension,
      String material,
      String style,
      String acceptanceNotes) {}

  record Attachment(String itemId, String materialId, String name, String type) {}

  record Quote(
      String id,
      int quoteVersion,
      String state,
      String terms,
      int goodsFen,
      int totalFen,
      LocalDateTime validUntil) {}

  record ListRow(
      String id,
      int version,
      String number,
      String title,
      String factoryId,
      String state,
      LocalDateTime updatedAt) {}

  /** 按 id, lock 查询并按调用条件锁定 custom_requirement 记录。 */
  Requirement selectCustomRequirementById(@Param("id") String id, @Param("lock") boolean lock);

  /** 按 buyer, factory, status 分页查询 custom_requirement 记录。 */
  List<ListRow> selectCustomRequirementPageByQuery(
      @Param("buyer") String buyer,
      @Param("factory") String factory,
      @Param("status") String status,
      @Param("page") PageQuery page);

  /** 按 buyer, factory, status 统计 custom_requirement 记录。 */
  long selectCustomRequirementCountByQuery(
      @Param("buyer") String buyer,
      @Param("factory") String factory,
      @Param("status") String status);

  /** 按 id 查询 custom_requirement_item 记录。 */
  List<Item> selectRequirementItemsByRequirementId(@Param("id") String id);

  /** 按 id 查询 custom_requirement_attachment 记录。 */
  List<Attachment> selectAttachmentsByRequirement(@Param("id") String id);

  /** 按 id, factory 查询 custom_quote 记录。 */
  List<Quote> selectQuotesByRequirement(@Param("id") String id, @Param("factory") boolean factory);

  /** 按 id 查询 custom_order 记录。 */
  String orderId(@Param("id") String id);

  /** 按 id 查询 factory_profile 记录。 */
  boolean activeFactory(@Param("id") String id);

  /** 按 id, factory 查询 prop_sku 记录。 */
  boolean existsSkuForFactory(@Param("id") String id, @Param("factory") String factory);

  /** 按 id, buyer, key 新增 custom_requirement 记录。 */
  int insertCustomRequirement(
      @Param("id") String id,
      @Param("buyer") String buyer,
      @Param("key") String key,
      @Param("draft") Draft draft,
      @Param("at") LocalDateTime at);

  /** 按 id, draft, budget 更新 custom_requirement 记录。 */
  int updateCustomRequirement(
      @Param("id") String id,
      @Param("draft") Draft draft,
      @Param("budget") String budget,
      @Param("at") LocalDateTime at);

  /** 按 id 删除 custom_requirement_item 记录。 */
  int clearItems(@Param("id") String id);

  /** 按 id 删除 custom_requirement_attachment 记录。 */
  int clearAttachments(@Param("id") String id);

  /** 按 id, root, item 新增 custom_requirement_item 记录。 */
  int insertItem(
      @Param("id") String id,
      @Param("root") String root,
      @Param("item") RequirementItem item,
      @Param("sort") int sort,
      @Param("at") LocalDateTime at);

  /** 按 id, root, item 新增 custom_requirement_attachment 记录。 */
  int insertCustomRequirementAttachment(
      @Param("id") String id,
      @Param("root") String root,
      @Param("item") String item,
      @Param("buyer") String buyer,
      @Param("ref") EvidenceRef ref,
      @Param("retained") boolean retained,
      @Param("sort") int sort,
      @Param("at") LocalDateTime at);

  /** 按 id, state, reason 更新 custom_requirement 记录。 */
  int transitionRequirementState(
      @Param("id") String id,
      @Param("state") String state,
      @Param("reason") String reason,
      @Param("at") LocalDateTime at);

  /** 按 id, root, factory 新增 custom_quote 记录。 */
  int saveQuote(
      @Param("id") String id,
      @Param("root") String root,
      @Param("factory") String factory,
      @Param("sequence") int sequence,
      @Param("terms") String terms,
      @Param("quoteTerms") QuoteTerms quoteTerms,
      @Param("goods") int goods,
      @Param("total") int total,
      @Param("at") LocalDateTime at);

  /** 按 id, at 更新 custom_quote 记录。 */
  int publishCustomQuote(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 按 root, state, at 更新 custom_quote 记录。 */
  int retireCustomQuote(
      @Param("root") String root, @Param("state") String state, @Param("at") LocalDateTime at);

  /** 按 root, at 更新 custom_quote 记录。 */
  int retireDrafts(@Param("root") String root, @Param("at") LocalDateTime at);

  /** 按 id, at 更新 custom_quote 记录。 */
  int acceptCustomQuote(@Param("id") String id, @Param("at") LocalDateTime at);
}
