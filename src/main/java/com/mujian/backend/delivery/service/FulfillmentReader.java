package com.mujian.backend.delivery.service;

import com.mujian.backend.delivery.vo.FulfillmentViews;

/** 履约上下文不创建草稿或推进到期事实。 */
public interface FulfillmentReader {
  /** 读取当前角色获权的履约节点与可用动作。 */
  FulfillmentViews.Fulfillment read(String actor, String orderId, boolean supplier, boolean admin);
}
