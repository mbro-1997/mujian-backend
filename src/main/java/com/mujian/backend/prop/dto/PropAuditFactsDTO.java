package com.mujian.backend.prop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class PropAuditFactsDTO {
  /** 构造当前数据对象，保留约定字段的类型和空值。 */
  private PropAuditFactsDTO() {}

  public record StatusVersion(
      @JsonProperty("status") String status, @JsonProperty("version") Integer version) {}

  public record SnStatus(
      @JsonProperty("status") String status,
      @JsonProperty("version") Integer version,
      @JsonProperty("qr_code_id") String qrCodeId) {}

  public record SkuOptions(
      @JsonProperty("type_code") String typeCode,
      @JsonProperty("style_codes") List<String> styleCodes,
      @JsonProperty("transport_suggestion") String transportSuggestion,
      @JsonProperty("version") Integer version) {}

  public record PreviousQr(@JsonProperty("supersedes_qr_id") String supersedesQrId) {}

  public record QrIdentity(
      @JsonProperty("qr_id") String qrId, @JsonProperty("qr_code_id") String qrCodeId) {}

  public record CaseState(
      @JsonProperty("schema_version") Integer schemaVersion,
      @JsonProperty("source_type") String sourceType,
      @JsonProperty("source_id") String sourceId,
      @JsonProperty("status") String status) {}

  public record CaseOutcome(
      @JsonProperty("schema_version") Integer schemaVersion,
      @JsonProperty("source_type") String sourceType,
      @JsonProperty("source_id") String sourceId,
      @JsonProperty("status") String status,
      @JsonProperty("action_id") String actionId) {}

  public record ReviewRequest(
      @JsonProperty("schema_version") Integer schemaVersion,
      @JsonProperty("source_type") String sourceType,
      @JsonProperty("source_id") String sourceId,
      @JsonProperty("decision") String decision,
      @JsonProperty("request_hash") String requestHash,
      @JsonProperty("expected_version") Integer expectedVersion,
      @JsonProperty("reason") String reason) {}

  public record OperationSnapshot(
      @JsonProperty("schema_version") Integer schemaVersion,
      @JsonProperty("operation_code") String operationCode,
      @JsonProperty("sku_id") String skuId,
      @JsonProperty("target_type") String targetType,
      @JsonProperty("target_id") String targetId,
      @JsonProperty("facts") Object facts) {}
}
