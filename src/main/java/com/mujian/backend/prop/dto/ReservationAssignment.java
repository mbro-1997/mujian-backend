package com.mujian.backend.prop.dto;

import java.util.List;

/** 在库存根锁内选出的确定SN，随后写入同一批次快照和占用。 */
public record ReservationAssignment(
    String skuId, String snapshotItemId, int quantity, List<String> snIds) {}
