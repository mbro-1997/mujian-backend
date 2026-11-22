package com.mujian.backend.delivery.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.common.enums.IssueScene;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IssueReviewMapper {
  record Row(String id,String orderId,String orderNo,String factoryId,String factoryName,int version,LocalDateTime createdAt,
      String caseId,int caseVersion,String status, IssueScene scene,String effect,String primaryIssueType,String summary,
      String arbitrationId,String supplementRequest) {}

  record Item(String issueId,String itemId,String issueType,int quantity,String description) {}
  record Evidence(String issueId,String evidenceId) {}
  record Filter(String id,String orderId,String buyer,String status) {}

  /** 买家谓词应用于查询和计数，详情自动过滤其他人的问题。 */
  List<Row> selectRows(@Param("filter") Filter filter,@Param("page") PageQuery page);
  /** 按 filter 统计 order_issue_report 记录。 */
  long countRows(@Param("filter") Filter filter);
  /** 按 ids 查询 order_issue_item 记录。 */
  List<Item> selectItems(@Param("ids") List<String> ids);
  /** 按 ids 查询 admin_case_relation 记录。 */
  List<Evidence> selectEvidence(@Param("ids") List<String> ids);

  /** 订单根锁内按子版本裁决，保留原上报内容。 */
  int updateStatus(@Param("id") String id,@Param("version") int version,@Param("status") String status,
      @Param("at") LocalDateTime at);

  /** 买家补充说明是一条具名文本证据，保留初次上报与每次补充。 */
  void insertSupplementText(@Param("id") String id,@Param("issue") Row issue,@Param("actor") String actor,
      @Param("text") String text,@Param("at") LocalDateTime at);

  /** 按 issue, status, at 更新 admin_case 记录。 */
  int changeCaseStatus(@Param("issue") Row issue,@Param("status") String status,@Param("at") LocalDateTime at);

  /** 平台要求补证的原因与问题版本同事务保留，使用结果可重放的动作来源。 */
  void insertEvidenceRequest(@Param("id") String id,@Param("issue") Row issue,@Param("actor") String actor,
      @Param("reason") String reason,@Param("requestId") String requestId,@Param("at") LocalDateTime at);
}
