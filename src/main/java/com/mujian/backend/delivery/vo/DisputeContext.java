package com.mujian.backend.delivery.vo;

import java.util.List;

/** 订单详情和履约页以同一组来源编号导航。 */
public record DisputeContext(List<String> issueIds, String activeExtensionId, List<String> claimIds,
    List<String> arbitrationIds, List<String> receivableIds, String currentDueDate, String latestExtensionPaidAt,com.mujian.backend.delivery.service.OverdueFees.View overdueFee) {}
