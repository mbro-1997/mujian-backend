package com.mujian.backend.scheme.domain;

import com.mujian.backend.planning.domain.SnapshotV1;
import java.util.List;

/** 由服务端商品快照白名单组成，隐藏价格时从JSON源头剔除金额。 */
public record ShareSnapshot(
    String name,
    String description,
    String status,
    int contentVersion,
    boolean hidePrice,
    List<Item> items) {
  public record Item(SnapshotV1 display, int quantity, String remark, int sortOrder) {}
}
