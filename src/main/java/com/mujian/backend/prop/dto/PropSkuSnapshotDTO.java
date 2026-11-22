package com.mujian.backend.prop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PropSkuSnapshotDTO(
    @JsonProperty("schema_version") Integer schemaVersion,
    @JsonProperty("sku_id") String skuId,
    @JsonProperty("sku_code") String skuCode,
    @JsonProperty("factory_id") String factoryId,
    @JsonProperty("asset_type") String assetType,
    @JsonProperty("sku_status") String skuStatus,
    @JsonProperty("sku_version") Integer skuVersion,
    @JsonProperty("fields") PropSkuFieldsDTO fields,
    @JsonProperty("initial_quantity") Integer initialQuantity,
    @JsonProperty("previous_audit_id") String previousAuditId) {}
