package com.mujian.backend.dispute.dto;
import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.payment.dto.CashComponents;
import java.util.List;
public record ArbitrationInput(int expectedVersion,String responsibility,int lossFen,int rentDeductionFen,CashComponents refunds,List<Outcome> stockOutcomes,String summary,String refundBasis){
  public record Outcome(String itemId,String snId,int quantity,String result){}
  public record Open(int expectedVersion,String sourceType,String sourceId,String summary,IssueInput.Evidence evidence){}
  public record Request(int expectedVersion,String party,String reason){}
  public record Confirm(int expectedVersion,String decisionId){}
}
