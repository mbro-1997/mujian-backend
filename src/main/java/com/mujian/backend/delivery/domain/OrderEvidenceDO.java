package com.mujian.backend.delivery.domain;

import static com.mujian.backend.common.support.D2Values.time;

import com.mujian.backend.delivery.vo.EvidenceViews;
import java.time.LocalDateTime;

/** 私有存储位置只在服务内使用，页面DTO显式剔除。 */
public record OrderEvidenceDO(
    String id,
    String orderId,
    String itemId,
    String legId,
    String statementId,
    String checkId,
    String node,
    String scene,
    String storageKey,
    String receiptId,
    String objectVersion,
    String text,
    String owner,
    String role,
    LocalDateTime capturedAt,
    LocalDateTime createdAt,
    LocalDateTime removedAt,String issueId,String claimId,String arbitrationId) {
  /** 公开字段由已授权节点投影，不返回原始对象定位。 */
  public EvidenceViews.Evidence visible() {
    return new EvidenceViews.Evidence(
        id,
        node,
        scene,
        "photo",
        itemId,
        legId,
        statementId,
        time(createdAt),
        time(capturedAt),
        text,issueId,claimId,arbitrationId);
  }
}
