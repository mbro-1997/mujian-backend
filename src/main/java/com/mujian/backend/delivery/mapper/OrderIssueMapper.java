package com.mujian.backend.delivery.mapper;

import com.mujian.backend.delivery.dto.IssueInput;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderIssueMapper {
  record ItemSource(String id,String skuId,int quantity) {}

  /** 批量核对道具明细及实物数量属于同一订单。 */
  List<ItemSource> selectOrderItems(@Param("orderId") String orderId);

  /** 新问题与当前核验关联，使用请求号和买家共同去重。 */
  void insertIssue(@Param("issueId") String issueId,@Param("orderId") String orderId,@Param("checkId") String checkId,
      @Param("actor") String actor,@Param("key") String key,@Param("input") IssueInput input,@Param("at") LocalDateTime at);

  /** 按道具和问题类型记录本次实际受影响数量。 */
  void insertIssueItem(@Param("id") String id,@Param("issueId") String issueId,@Param("orderId") String orderId,
      @Param("skuId") String skuId,@Param("item") IssueInput.Item item,@Param("at") LocalDateTime at);

  /** 批量验证本人本单同节点照片，已绑定材料仅作新引用。 */
  List<String> selectOwnedEvidenceIds(@Param("orderId") String orderId,@Param("actor") String actor,
      @Param("node") String node,@Param("ids") List<String> ids);

  /** 新照片绑定到本次问题主来源。 */
  int bindIssueEvidence(@Param("id") String id,@Param("issueId") String issueId,@Param("orderId") String orderId);

  /** 首次暂停保存剩余毫秒，后续暂停只递增核验版本，避免旧页面继续确认。 */
  int reportReceiptIssue(@Param("checkId") String checkId,@Param("version") int version,
      @Param("pause") boolean pause,@Param("at") LocalDateTime at);

  /** 原配送摘要与本单核验读取同一截止。 */
  void synchronizeReceiptDeadline(@Param("orderId") String orderId,@Param("at") LocalDateTime at);

  /** 使用中提交按同一订单版本串行裁决。 */
  int advanceOrderVersion(@Param("orderId") String orderId,@Param("version") int version,@Param("at") LocalDateTime at);
  /** 重启后按订单游标扫描处于暂停的收货核验。 */
  List<String> selectFrozenReceipts(@Param("afterId") String afterId);

  /** 在原计时基础上设置新截止，成功后清空冻结值。 */
  int resumeReceipt(@Param("orderId") String orderId, @Param("at") LocalDateTime at);
}
