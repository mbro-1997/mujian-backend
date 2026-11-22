package com.mujian.backend.delivery.dto;

import com.mujian.backend.common.enums.IssueEffect;
import com.mujian.backend.common.enums.IssueScene;
import com.mujian.backend.common.enums.IssueType;
import java.util.List;

public record IssueInput(int expectedVersion, IssueScene scene, IssueEffect effect, IssueType primaryIssueType,
    String summary,List<Item> items, Evidence evidence) {
  public record Item(String itemId, IssueType issueType, int quantity, String description) {}
  public record Supplement(int expectedVersion, String summary, Evidence evidence) {}
  public record Reason(int expectedVersion, String reason) {}
  public record Evidence(List<String> evidenceIds,List<String> uploadReceiptIds) {}
}
