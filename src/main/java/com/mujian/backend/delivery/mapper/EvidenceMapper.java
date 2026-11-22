package com.mujian.backend.delivery.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.delivery.domain.OrderEvidenceDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface EvidenceMapper {
  record Query(String orderId, String node, String statementId, boolean expenses,String actor,String role,boolean claims,boolean arbitrations,boolean issues, PageQuery page) {}

  record Statement(String id, String status, int version) {}

  /** 仅接收已预检的对象版本和真实证据归属。 */
  void insertEvidence(@Param("row") OrderEvidenceDO row);

  /** 按订单、角色和节点过滤列表。 */
  List<OrderEvidenceDO> selectPage(@Param("query") Query query);

  /** 与列表使用完全相同的可见条件。 */
  long countPage(@Param("query") Query query);

  /** 直读使用与分页相同的业务权限。 */
  OrderEvidenceDO selectById(@Param("query") Query query, @Param("id") String id);

  /** 业务草稿原记录保留，仅标记移除时间。 */
  int markOrderEvidenceRemoved(
      @Param("orderId") String orderId, @Param("id") String id, @Param("at") LocalDateTime at);

  /** 凭证唯一引用包含已移除行，不能二次消费。 */
  boolean receiptUsed(@Param("receiptId") String receiptId);

  /** 可选道具明细仅接受属于本单的行。 */
  boolean ownsItem(@Param("orderId") String orderId, @Param("itemId") String itemId);

  /** 取本单指定费用申报及版本，不能混用工厂或任意段。 */
  Statement selectStatement(
      @Param("orderId") String orderId, @Param("statementId") String statementId);

  /** 真实引用改变时一次推进所属根版本并重数有效照片。 */
  int advanceRoot(
      @Param("node") String node,
      @Param("id") String id,
      @Param("orderId") String orderId,
      @Param("version") int version,
      @Param("at") LocalDateTime at);
}
