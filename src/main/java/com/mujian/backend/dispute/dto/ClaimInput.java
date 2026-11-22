package com.mujian.backend.dispute.dto;

import com.mujian.backend.delivery.dto.IssueInput;
import java.util.List;

public record ClaimInput(int expectedVersion,String problemType,Integer claimedFen,String summary,IssueInput.Evidence evidence,List<IssueInput.Item> items) {
  public record Review(int expectedVersion,String decision,Integer lossFen,String remark) {}
}
