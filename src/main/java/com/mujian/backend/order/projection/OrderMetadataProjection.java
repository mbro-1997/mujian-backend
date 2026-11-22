package com.mujian.backend.order.projection;

/** 订单列表所需快照工厂名和配送状态，批量读取避免逐单查询。 */
public record OrderMetadataProjection(
    String orderId,
    String factoryName,
    String deliveryStatus,
    String fulfillmentStage,
    java.time.LocalDateTime nextDeadlineAt,
    String returnMode,
    boolean expenseRequired,
    boolean settledBatch,
    String pausedIssueScene,
    String terminationDisplayStatus,
    boolean issueExists,boolean extensionExists,boolean factoryExtensionExists,boolean pendingExtension,
    boolean claimExists,boolean buyerClaimExists,boolean claimActive,boolean buyerClaimActive,
    boolean arbitrationExists,boolean arbitrationActive,boolean debtExists,boolean claimWindow,boolean claimStockWork) {}
