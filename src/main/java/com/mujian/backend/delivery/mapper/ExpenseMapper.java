package com.mujian.backend.delivery.mapper;

import com.mujian.backend.delivery.domain.ExpenseStatementDO;
import com.mujian.backend.delivery.dto.ExpenseDtos;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ExpenseMapper {
  record Basis(String id, String type, String mode, String status, String snapshot, int pool) {}

  record Evidence(String id, String legId, String scene, String statementId) {}

  record LegWrite(
      String id, String source, String carrier, String orderNo, int base, int extra, int total) {}

  record Draft(
      String id,
      String orderId,
      String factoryId,
      int number,
      int version,
      LegWrite outbound,
      LegWrite returnLeg,
      int total,
      int pool,
      String details,
      int count,
      LocalDateTime at) {}

  /** 按原方向取得往返两段的冻结计费依据。 */
  List<Basis> selectTransportLegsByOrder(@Param("orderId") String orderId);

  /** 定位订单最新的费用申报版本。 */
  ExpenseStatementDO selectLatestExpenseStatement(@Param("orderId") String orderId);

  /** 校验查看范围并取得原记录的金额和处理事实。 */
  ExpenseStatementDO selectExpenseDetail(@Param("orderId") String orderId, @Param("id") String id);

  /** 在授权数据范围内分页并返回总数。 */
  List<ExpenseStatementDO> selectExpenseStatementPageByQuery(@Param("query") ExpenseDtos.Query query);

  /** 用列表相同条件计算全部匹配记录。 */
  long selectOrderTransportExpenseStatementCountByQuery(@Param("query") ExpenseDtos.Query query);

  /** 按申报版本读取两段可验证的照片引用。 */
  List<Evidence> selectExpenseEvidenceByOrderAndStatement(@Param("orderId") String orderId, @Param("id") String statementId);

  /** 创建带原运输段快照的费用草稿。 */
  void insertDraft(@Param("draft") Draft draft);

  /** 按版本更新仍可编辑的草稿。 */
  int updateDraft(@Param("draft") Draft draft);

  /** 重新校验两段计价、运输完成和凭证后提交。 */
  int submitTransportExpenseStatement(
      @Param("id") String id,
      @Param("version") int version,
      @Param("details") String details,
      @Param("at") LocalDateTime at);

  /** 在原版本上互斥地批准或退回，批准后按原支付来源处理余额。 */
  int reviewTransportExpenseStatement(
      @Param("id") String id,
      @Param("version") int version,
      @Param("approve") boolean approve,
      @Param("actor") String actor,
      @Param("reason") String reason,
      @Param("at") LocalDateTime at);

  /** 只更新已核准方向的认定费用。 */
  int approveLeg(
      @Param("orderId") String orderId, @Param("leg") LegWrite leg, @Param("at") LocalDateTime at);

  /** 按冻结暂收池写入核准额、线上应结额和退款责任。 */
  int approveAccount(@Param("row") ExpenseStatementDO approved, @Param("at") LocalDateTime at);
}
