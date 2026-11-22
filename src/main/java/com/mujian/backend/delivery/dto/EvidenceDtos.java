package com.mujian.backend.delivery.dto;

import com.mujian.backend.common.enums.EvidenceNode;
import java.util.List;

/** 证据绑定只接受上传凭证及业务说明，存储定位由服务端解析。 */
public final class EvidenceDtos {
  /** 仅承载本领域的结构定义或纯计算。 */
  private EvidenceDtos() {}

  public record Input(
      String receiptId,
      EvidenceNode node,
      String scene,
      String itemId,
      String statementId,
      String transportLegId,
      String capturedAt,
      String text) {}

  public record Attach(int expectedVersion, List<Input> materials) {}
}
