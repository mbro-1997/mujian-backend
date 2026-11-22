package com.mujian.backend.delivery.service;

import com.mujian.backend.delivery.mapper.IssuePlanMapper;
import com.mujian.backend.payment.dto.CashComponents;
import tools.jackson.databind.JsonNode;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.enums.IssueType;
import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.delivery.mapper.IssueReviewMapper;
import com.mujian.backend.delivery.vo.IssueView;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.service.TradingAccess;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 身份过滤先于分页和聚合，同一快照返回原单、问题、证据及管理事项版本。 */
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class IssueReader {
  private final IssueReviewMapper issueReviewMapper;
  private final VerificationAccess verificationAccess;
  private final TradingAccess tradingAccess;
  private final com.mujian.backend.delivery.mapper.IssuePlanMapper issuePlanMapper;
  private final tools.jackson.databind.ObjectMapper json;

  public IssueReader(IssueReviewMapper issueReviewMapper,VerificationAccess verificationAccess,TradingAccess tradingAccess,com.mujian.backend.delivery.mapper.IssuePlanMapper issuePlanMapper,tools.jackson.databind.ObjectMapper json) {
    this.issuePlanMapper=issuePlanMapper;
    this.json=json;
    this.issueReviewMapper=issueReviewMapper;
    this.verificationAccess=verificationAccess;
    this.tradingAccess=tradingAccess;
  }

  /** 按当前身份和筛选条件返回分页摘要。 */
  public PageResult<IssueView> page(String actor,boolean admin,String orderId,String status,PageQuery page) {
    boolean write=authorize(actor,admin);
    IssueReviewMapper.Filter filter=new IssueReviewMapper.Filter(null,orderId,admin ? null : actor,status);
    List<IssueView> items=assemble(issueReviewMapper.selectRows(filter,page),admin,write,actor);
    return PageResult.of(items,issueReviewMapper.countRows(filter),page);
  }

  /** 组装当前身份有权查看的明细与操作。 */
  public IssueView detail(String actor,boolean admin,String id) {
    boolean write=authorize(actor,admin);
    List<IssueReviewMapper.Row> rows=issueReviewMapper.selectRows(new IssueReviewMapper.Filter(id,null,admin ? null : actor,null),new PageQuery(1,1));
    if (rows.isEmpty()) throw missing();
    return assemble(rows,admin,write,actor).get(0);
  }

  /** 核验本次实际操作所需身份和权限。 */
  private boolean authorize(String actor,boolean admin) {
    if (!admin) {
      tradingAccess.buyer(actor);
      return false;
    }
    verificationAccess.admin(actor,"admin:receipt-issue:read");
    return verificationAccess.has(actor,"admin:receipt-issue:resolve");
  }

  /** 合并来源及处理记录用于详情展示。 */
  private List<IssueView> assemble(List<IssueReviewMapper.Row> rows,boolean admin,boolean write,String actor) {
    if (rows.isEmpty()) return List.of();
    List<String> ids=rows.stream().map(IssueReviewMapper.Row::id).toList();
    Map<String,List<IssueInput.Item>> items=issueReviewMapper.selectItems(ids).stream().collect(Collectors.groupingBy(IssueReviewMapper.Item::issueId,
        Collectors.mapping(item -> new IssueInput.Item(item.itemId(), IssueType.valueOf(item.issueType()),item.quantity(),item.description()),Collectors.toList())));
    Map<String,List<String>> evidence=issueReviewMapper.selectEvidence(ids).stream().collect(Collectors.groupingBy(IssueReviewMapper.Evidence::issueId,
        Collectors.mapping(IssueReviewMapper.Evidence::evidenceId,Collectors.toList())));
    List<IssueView> result=new ArrayList<>();
    for (IssueReviewMapper.Row issueReviewRow : rows) {
      List<String> actions=new ArrayList<>();
      if (!admin && "waiting_evidence".equals(issueReviewRow.status())) actions.add("supplement");
      IssuePlanMapper.Action active=issuePlanMapper.selectActive(issueReviewRow.orderId(),issueReviewRow.id(),false);
      if (write && active==null && Set.of("submitted","processing").contains(issueReviewRow.status())) actions.add("request_evidence");
      boolean open=Set.of("submitted","processing","waiting_evidence").contains(issueReviewRow.status());
      boolean free=issuePlanMapper.selectActive(issueReviewRow.orderId(),null,true)==null && !issuePlanMapper.hasActiveClaimOrArbitration(issueReviewRow.orderId());
      if (admin && open && free && active==null && verificationAccess.has(actor,"admin:arbitration:decide"))actions.add("open_arbitration");
      if (write && open && (free || active!=null) && (active==null || !active.locked())) actions.add(active==null?"confirm_plan":"revise_plan");
      String excludeAction=active!=null && !active.locked()?active.id():null;
      List<IssuePlanMapper.Source> sources=admin && open ? issuePlanMapper.selectRefundFundingSourcesByOrder(issueReviewRow.orderId(),excludeAction) : List.<com.mujian.backend.delivery.mapper.IssuePlanMapper.Source>of();
      CashComponents limits=sources.isEmpty()?null:IssuePlans.limits(sources,issuePlanMapper.selectOrderDeductionOffsets(issueReviewRow.orderId(),excludeAction));
      result.add(new IssueView(issueReviewRow.id(),issueReviewRow.orderId(),issueReviewRow.orderNo(),issueReviewRow.factoryId(),issueReviewRow.factoryName(),issueReviewRow.version(),
          time(issueReviewRow.createdAt()),issueReviewRow.caseId(),issueReviewRow.caseVersion(),issueReviewRow.status(),issueReviewRow.scene().name(),issueReviewRow.effect(),issueReviewRow.primaryIssueType(),
          issueReviewRow.summary(),items.getOrDefault(issueReviewRow.id(),List.of()),evidence.getOrDefault(issueReviewRow.id(),List.of()),plan(active),limits,
          issueReviewRow.arbitrationId(),issueReviewRow.supplementRequest(),List.copyOf(actions)));
    }
    return result;
  }

  /** 读取处置计划及执行状态。 */
  private tools.jackson.databind.JsonNode plan(com.mujian.backend.delivery.mapper.IssuePlanMapper.Action action) {
    if (action==null) return null;
    JsonNode p=json.readTree(action.payload());
    JsonNode t=p.path("termination");
    return json.valueToTree(view("actionId",action.id(),"version",action.schemeVersion(),"direction",p.get("fulfillment_decision"),
        "solutionSummary",p.get("solution_summary"),"agreement",p.get("agreement"),"references",p.get("references"),
        "serviceRefund",p.get("service_refund"),"recovery",t.get("return_plan"),"freightResponsibility",t.get("freight_plan"),
        "depositReason",t.get("deposit_deduction_reason"),"goodsLocation",t.get("goods_location"),"refundBasis",t.get("refund_basis"),
        "refundComponents",action.rent()==null?null:new com.mujian.backend.payment.dto.CashComponents(action.rent(),action.deposit(),action.freight()),
        "locked",action.locked(),"executionStatus",action.status(),"refunds",issuePlanMapper.selectPlannedRefundsByAction(action.id())));
  }
}
