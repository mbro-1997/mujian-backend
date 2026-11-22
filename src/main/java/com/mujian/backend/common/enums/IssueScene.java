package com.mujian.backend.common.enums;

/** 两种问题来源分别使用收货核验和原订单版本。 */
public enum IssueScene {
  receipt, in_use;

  /** 取得该业务来源唯一可绑定的照片节点。 */
  public EvidenceNode evidenceNode() {
    return this == receipt ? EvidenceNode.receipt_check : EvidenceNode.in_use_report;
  }
}
