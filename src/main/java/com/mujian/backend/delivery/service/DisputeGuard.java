package com.mujian.backend.delivery.service;

/** D5需在既有订单根锁内提供真实事实；未知能力绝不等价于没有争议。 */
public interface DisputeGuard {
  record Facts(boolean ready, boolean paused, boolean claimActive, boolean terminationActive) {}

  /** 分页必须先按真实互斥事实过滤。未接入可查询事实时不返回正常履约待办。 */
  default java.util.Optional<java.util.Set<String>> blockedOrders(
      String buyerId, String factoryId) {
    return java.util.Optional.empty();
  }

  /** 调用者已持有订单根，读取同一事务内的问题、索赔和终止事实。 */
  default boolean terminationSettled(String orderId){return false;}
  Facts UNKNOWN = new Facts(false, false, false, false);

  /** 未提供批量来源时，页面保守限制操作。 */
  default java.util.Map<String,Facts> pageFacts(java.util.List<String> ids) {
    return java.util.Map.of();
  }

  /** 读取本订单的争议、暂停及终止事实。 */
  Facts inspect(String orderId);
}
