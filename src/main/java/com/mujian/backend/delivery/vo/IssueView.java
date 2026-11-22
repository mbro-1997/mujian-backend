package com.mujian.backend.delivery.vo;

import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.payment.dto.CashComponents;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record IssueView(String id,String orderId,String orderNo,String factoryId,String factoryName,int version,String createdAt,
    String caseId,int caseVersion,String status,String scene,String effect,String primaryIssueType,String summary,
    List<IssueInput.Item> items,List<String> evidenceIds,JsonNode plan,CashComponents remainingRefundLimits,
    String arbitrationId,String supplementRequest,List<String> availableActions) {}
