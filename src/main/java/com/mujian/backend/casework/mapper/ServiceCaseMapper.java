package com.mujian.backend.casework.mapper;

import org.apache.ibatis.annotations.Param;
import com.mujian.backend.casework.dto.*;
import com.mujian.backend.casework.projection.*;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.customer.vo.ServiceViews.Source;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceCaseMapper {
  /** 按案件根过滤并计数。 */
  long countCases(@Param("filter") ServiceCaseQueryDTO filter);

  /** 一次取当前页，主来源不放大案件数量。 */
  List<ServiceCaseSummaryProjection> selectCaseSummaries(@Param("filter") ServiceCaseQueryDTO filter, @Param("page") PageQuery page);

  /** 读取已有业务案件及主来源。 */
  ServiceCaseDetailProjection selectCase(@Param("id") String id);

  /** 读取结案的状态、版本及结果归属依据。 */
  CaseResolutionProjection selectCaseResolutionById(@Param("id") String id);

  /** 从已锁定的案件读取补偿对象，沿用租赁订单受益人回退。 */
  String selectCaseBeneficiaryById(@Param("id") String id);

  /** 案件处理按版本和根锁串行。 */
  String lockCase(@Param("id") String id);

  /** 人工建案无需渠道回调。 */
  void insertCase(@Param("id") String id, @Param("actor") String actor, @Param("input") CreateServiceCaseDTO input, @Param("at") LocalDateTime at);

  /** 保存已核实来源或人工登记用户及实际诉求。 */
  void insertSource(@Param("id") String id, @Param("caseId") String caseId, @Param("source") Source source, @Param("input") CreateServiceCaseDTO input, @Param("at") LocalDateTime at);

  /** 统一的处理动作留痕。 */
  void insertAction(@Param("id") String id,
      @Param("caseId") String caseId,
      @Param("actor") String actor,
      @Param("type") String type,
      @Param("reason") String reason,
      @Param("result") String result,
      @Param("at") LocalDateTime at);

  /** 只变更案件和解决说明。 */
  int resolveCase(@Param("id") String id,
      @Param("version") int version,
      @Param("actor") String actor,
      @Param("summary") String summary,
      @Param("refs") String refs,
      @Param("at") LocalDateTime at);

  /** 关联既有上下文，不生成渠道发送事实。 */
  int linkContext(@Param("id") String id, @Param("caseId") String caseId, @Param("at") LocalDateTime at);

  /** 结案保留咨询历史。 */
  void resolveContext(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 核对资金、优惠券或业务结果属于本受益人。 */
  boolean resultBelongs(@Param("user") String user, @Param("caseId") String caseId, @Param("source") Source source, @Param("ref") Source ref);
}
