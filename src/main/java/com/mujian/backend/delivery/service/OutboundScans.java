package com.mujian.backend.delivery.service;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.delivery.dto.FulfillmentDtos;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.dto.TradingCommand;

/** 出库实物核对及原成功扫描追溯。 */
public interface OutboundScans {
  /** 按平台当前码核对本单原分配SN，不接受客户端SN声明。 */
  FulfillmentViews.ScanResult scan(
      TradingCommand command, String orderId, FulfillmentDtos.ScanInput scanInput);

  /** 只列本厂本单成功扫描，失败不伪造进度。 */
  FulfillmentViews.ScanPage page(String actor, String orderId, PageQuery pageQuery);
}
