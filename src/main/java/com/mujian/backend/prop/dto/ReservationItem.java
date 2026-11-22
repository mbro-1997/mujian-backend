package com.mujian.backend.prop.dto;

/** 交易已核实的SKU、来源快照和请求数量，不接收客户端占用ID。 */
public record ReservationItem(String skuId, String snapshotItemId, int quantity) {}
