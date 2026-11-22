package com.mujian.backend.delivery.vo;

import java.util.List;

/** 私有照片只有独立查看接口签名，分页不携带对象路径。 */
public final class EvidenceViews {
  /** 仅承载本领域的结构定义或纯计算。 */
  private EvidenceViews() {}

  public record Upload(String receiptId, String expiresAt, String mimeType, int sizeBytes) {}

  public record Evidence(
      String id,
      String node,
      String scene,
      String type,
      String itemId,
      String transportLegId,
      String statementId,
      String uploadedAt,
      String capturedAt,
      String text,String issueId,String claimId,String arbitrationId) {}

  public record Page(List<Evidence> items, int page, int pageSize, String total) {}

  public record View(String url, String expiresAt) {}
}
