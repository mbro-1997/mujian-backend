package com.mujian.backend.settlement.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.settlement.dto.SettlementQuery;
import com.mujian.backend.settlement.vo.SettlementViews.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SettlementReadMapper {
  record Info(
      String receiver,
      String masked,
      LocalDateTime checked,
      String exception,
      LocalDateTime acknowledged) {}

  record Audit(String id, String digest, LocalDateTime at) {}

  /** 在授权数据范围内分页并返回总数。 */
  List<Summary> selectSettlementBatchPageByQuery(@Param("query") SettlementQuery query);

  /** 用列表相同条件计算全部匹配记录。 */
  long selectFactorySettlementBatchCountByQuery(@Param("query") SettlementQuery query);

  /** 检查指定批次是否对当前调用者可见。 */
  boolean existsVisibleBatch(@Param("id") String id, @Param("owner") String owner);

  /** 受工厂范围限制读取指定批次汇总。 */
  Summary selectSettlementBatchSummaryById(@Param("id") String id, @Param("owner") String owner);

  /** 读取原收款快照、异常和当前查看人的已阅事实。 */
  Info selectSettlementPaymentInfoByBatch(@Param("id") String id, @Param("actor") String actor);

  /** 限定该批次并分页读取订单结算明细。 */
  List<Item> selectSettlementItemsByBatch(@Param("id") String id, @Param("page") PageQuery page);

  /** 限定该批次并分页读取原来源分佣及解冻记录。 */
  List<Execution> selectWechatProfitSharingOrderExecutions(@Param("id") String id, @Param("page") PageQuery page);

  /** 计算当前批次已固化的明细数。 */
  long itemCount(@Param("id") String id);

  /** 计算当前批次的实际渠道执行单数。 */
  long executionCount(@Param("id") String id);

  /** 按稳定顺序汇集影响已阅快照的原始依据。 */
  List<String> selectFactsFingerprint(@Param("id") String id);

  /** 在管理员、目标和原键范围内核对原已阅回执。 */
  Audit selectAdminOperationAuditByActor(@Param("id") String batch, @Param("actor") String actor, @Param("key") String key);
}
