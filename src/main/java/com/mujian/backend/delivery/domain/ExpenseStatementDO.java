package com.mujian.backend.delivery.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mujian.backend.delivery.dto.ExpenseDtos.ExtraFee;
import java.time.LocalDateTime;
import java.util.List;

/** 申报版本读取显式映射，草稿零额与未输入另存于本版 JSON。 */
public record ExpenseStatementDO(
    String id,
    String orderId,
    String orderNo,
    String factoryId,
    String factoryName,
    int statementVersion,
    int version,
    String status,
    String outboundId,
    String returnId,
    String outboundSource,
    String returnSource,
    String outboundCarrier,
    String outboundOrderNo,
    String returnCarrier,
    String returnOrderNo,
    int outboundBase,
    int returnBase,
    int outboundTotal,
    int returnTotal,
    int approvedOutbound,
    int approvedReturn,
    int declaredTotal,
    int approvedTotal,
    int pool,
    int online,
    int overBudget,
    int refundable,
    int evidenceCount,
    String detailJson,
    LocalDateTime submittedAt,
    LocalDateTime reviewedAt,
    String reason,
    String refundJson,
    String outboundMode,
    String returnMode,
    String outboundSnapshot,
    String returnSnapshot,
    String outboundStatus,
    String returnStatus,
    boolean latestVersion) {
  public record LegEvidence(
      List<String> baseEvidenceIds, List<ExtraFee> extraFees, boolean baseProvided) {}

  public record Details(
      int schemaVersion,
      LegEvidence outbound,
      @JsonProperty("return") LegEvidence returnLeg,
      List<String> reusedEvidenceIds) {}
}
