package com.mujian.backend.casework.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.admin.service.CommandAuditService;
import com.mujian.backend.admin.service.CommandAuditService.Command;
import com.mujian.backend.casework.mapper.ServiceCaseMapper;
import com.mujian.backend.casework.vo.ServiceCaseViews.*;
import com.mujian.backend.casework.dto.*;
import com.mujian.backend.casework.projection.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.coupon.mapper.DistributionMapper;
import com.mujian.backend.coupon.projection.DistributionRows.*;
import com.mujian.backend.coupon.service.OperationsReader;
import com.mujian.backend.coupon.vo.OperationsViews.Issued;
import com.mujian.backend.customer.mapper.ServiceContextMapper;
import com.mujian.backend.customer.service.ServiceContextService;
import com.mujian.backend.customer.vo.ServiceViews.*;
import com.mujian.backend.factory.service.FactoryAccess;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class CustomerCaseService {
  private final ServiceCaseMapper serviceCaseMapper;
  private final ServiceContextMapper serviceContextMapper;
  private final ServiceContextService serviceContextService;
  private final CommandAuditService commandAuditService;
  private final FactoryAccess factoryAccess;
  private final DistributionMapper distributionMapper;
  private final OperationsReader operationsReader;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  /** 客服处理只写案件、上下文和独立补偿。 */
  public CustomerCaseService(
      ServiceCaseMapper serviceCaseMapper,
      ServiceContextMapper serviceContextMapper,
      ServiceContextService serviceContextService,
      CommandAuditService commandAuditService,
      FactoryAccess factoryAccess,
      DistributionMapper distributionMapper,
      OperationsReader operationsReader,
      Clock clock,
      ObjectMapper objectMapper) {
    this.serviceCaseMapper = serviceCaseMapper;
    this.serviceContextMapper = serviceContextMapper;
    this.serviceContextService = serviceContextService;
    this.commandAuditService = commandAuditService;
    this.factoryAccess = factoryAccess;
    this.distributionMapper = distributionMapper;
    this.operationsReader = operationsReader;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  /** 分页只关联案件主来源。 */
  public PageResult<Row> page(ServiceCaseQueryDTO filter, PageQuery page) {
    long count = serviceCaseMapper.countCases(filter);
    List<Row> items = new ArrayList<>();
    if (count > page.offset())
      for (ServiceCaseSummaryProjection summaryProjection : serviceCaseMapper.selectCaseSummaries(filter, page))
        items.add(
            new Row(
                summaryProjection.id(),
                summaryProjection.caseNo(),
                summaryProjection.caseType(),
                summaryProjection.caseStatus(),
                summaryProjection.title(),
                time(summaryProjection.deadlineAt()),
                summaryProjection.sourceId() == null ? null : new Source(summaryProjection.sourceType(), summaryProjection.sourceId()),
                summaryProjection.version()));
    return PageResult.of(items, count, page);
  }

  /** 保留既有领域结论，客服读回建案诉求及结果引用。 */
  public Detail detail(String id) {
    ServiceCaseDetailProjection detailProjection = serviceCaseMapper.selectCase(id(id));
    if (detailProjection == null) throw missing();
    return new Detail(
        detailProjection.id(),
        detailProjection.caseNo(),
        detailProjection.caseType(),
        detailProjection.caseStatus(),
        detailProjection.title(),
        time(detailProjection.deadlineAt()),
        detailProjection.sourceId() == null ? null : new Source(detailProjection.sourceType(), detailProjection.sourceId()),
        detailProjection.version(),
        detailProjection.summary(),
        detailProjection.resolutionSummary(),
        detailProjection.refsJson() == null
            ? List.of()
            : List.of(objectMapper.readValue(detailProjection.refsJson(), Source[].class)),
        detailProjection.contextId(),
        detailProjection.demand(),
        detailProjection.mainConflict());
  }

  /** 独立建案不要求微信已回调。有上下文时核对原本人和来源。 */
  public Detail create(Command command, CreateServiceCaseDTO createServiceCaseDTO) {
    return commandAuditService.execute(
        command,
        "admin:customer-service:create",
        "createServiceCase",
        "登记客服案件",
        "08",
        "admin_case",
        null,
        createServiceCaseDTO,
        Detail.class,
        Detail::id,
        () -> {
          factoryAccess.context(createServiceCaseDTO.userId(), false);
          Source source = createServiceCaseDTO.source();
          ContextRow context = null;
          if (createServiceCaseDTO.contextId() != null) {
            context = serviceContextMapper.selectContext(createServiceCaseDTO.contextId(), true);
            if (context == null) throw missing();
            if (!createServiceCaseDTO.userId().equals(context.userId()) || context.adminCaseId() != null)
              throw conflict(BusinessFailureReason.SOURCE_MISMATCH.name());
            Source original = new Source(context.sourceType(), context.sourceId());
            if (source != null && !source.equals(original)) throw conflict(BusinessFailureReason.SOURCE_MISMATCH.name());
            source = original;
          }
          if (source != null) serviceContextService.owned(createServiceCaseDTO.userId(), source);
          String id = Ids.next();
          LocalDateTime at = now(clock);
          serviceCaseMapper.insertCase(id, command.actor(), createServiceCaseDTO, at);
          serviceCaseMapper.insertSource(
              Ids.next(),
              id,
              source == null ? new Source("sys_user", createServiceCaseDTO.userId()) : source,
              createServiceCaseDTO,
              at);
          if (context != null && serviceCaseMapper.linkContext(context.id(), id, at) != 1)
            throw conflict(BusinessFailureReason.SOURCE_MISMATCH.name());
          serviceCaseMapper.insertAction(
              Ids.next(), id, command.actor(), "create_service_case", "登记客服案件", "{}", at);
          return detail(id);
        });
  }

  /** 解决说明不触发退款和库存变更。 */
  public Detail resolve(Command command, String id, ResolveServiceCaseDTO resolution) {
    return commandAuditService.execute(
        command,
        "admin:customer-service:resolve",
        "resolveServiceCase",
        "解决客服案件",
        "08",
        "admin_case",
        id,
        resolution,
        Detail.class,
        Detail::id,
        () -> {
          serviceCaseMapper.lockCase(id);
          CaseResolutionProjection resolutionProjection = serviceCaseMapper.selectCaseResolutionById(id);
          if (resolutionProjection == null) throw missing();
          if (!"customer_service".equals(resolutionProjection.caseType())
              || Set.of("closed", "cancelled").contains(resolutionProjection.caseStatus()))
            throw conflict(BusinessFailureReason.CASE_NOT_RESOLVABLE.name());
          if (resolutionProjection.version() != resolution.version()) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
          for (Source ref : resolution.resultRefs())
            if (!serviceCaseMapper.resultBelongs(
                resolutionProjection.beneficiary(), id, new Source(resolutionProjection.sourceType(), resolutionProjection.sourceId()), ref))
              throw conflict(BusinessFailureReason.SOURCE_MISMATCH.name());
          LocalDateTime at = now(clock);
          if (serviceCaseMapper.resolveCase(
                  id,
                  resolution.version(),
                  command.actor(),
                  resolution.resolutionSummary(),
                  objectMapper.writeValueAsString(resolution.resultRefs()),
                  at)
              != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
          serviceCaseMapper.resolveContext(id, at);
          serviceCaseMapper.insertAction(
              Ids.next(),
              id,
              command.actor(),
              "resolve_service_case",
              resolution.resolutionSummary(),
              objectMapper.writeValueAsString(resolution.resultRefs()),
              at);
          return detail(id);
        });
  }

  /** 按真实案件用户发独立补偿券，失败不更改案件状态。 */
  public Issued compensate(Command command, String id, CaseCompensationDTO compensation) {
    return commandAuditService.execute(
        command,
        "admin:coupon:issue",
        "issueCaseCoupon",
        "案件补偿发券",
        "12",
        "admin_case",
        id,
        compensation,
        Issued.class,
        x -> id,
        () -> {
          if (serviceCaseMapper.lockCase(id) == null) throw missing();
          String beneficiary = serviceCaseMapper.selectCaseBeneficiaryById(id);
          if (!compensation.beneficiaryUserId().equals(beneficiary)
              || !D2Codes.CODE_DEMANDER.equals(factoryAccess.context(beneficiary, true).roleCode()))
            throw conflict(BusinessFailureReason.BENEFICIARY_MISMATCH.name());
          Template template = distributionMapper.lockTemplate(compensation.templateId());
          LocalDateTime at = now(clock);
          if (template == null || !template.issuable(at)) throw conflict(BusinessFailureReason.TEMPLATE_NOT_ISSUABLE.name());
          String couponId = Ids.next();
          CouponWrite coupon =
              new CouponWrite(couponId, beneficiary, "compensation:" + couponId, Ids.next());
          distributionMapper.insertCoupons(List.of(coupon), template, "admin_compensation", id, at);
          distributionMapper.insertIssuedLogs(
              List.of(coupon), FulfillmentRole.admin.name(), command.actor(), "admin_case", id, compensation.reason(), at);
          distributionMapper.incrementTemplate(template.id(), 1, at);
          serviceCaseMapper.insertAction(
              Ids.next(),
              id,
              command.actor(),
              "issue_compensation_coupon",
              compensation.reason(),
              objectMapper.writeValueAsString(Map.of("userCouponId", couponId)),
              at);
          return operationsReader.readIssued(couponId, false);
        });
  }
}
