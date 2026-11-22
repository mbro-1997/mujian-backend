package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.support.*;
import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.dto.*;
import com.mujian.backend.order.vo.TradingReceipt;
import java.util.*;
import org.springframework.transaction.annotation.*;

/** 租赁订单接拒、取消和到期处理。 */
public interface RentalOrderCommands {
  /** 接拒只允许截止前的待接原单，库存保持原已付占用，接单不重新选SN。 */
  TradingReceipt factory(
      TradingCommand tradingCommand, String orderId, int expectedVersion, String reason);

  /** 同号先恢复；新确认必须与锁内重新计算的档位和原单版本一致。 */
  TradingReceipt cancel(
      TradingCommand tradingCommand, String orderId, OrderDtos.Cancel cancelInput);

  /** 系统到期使用固定系统用户；与人工接单、拒绝、取消争同一根锁。 */
  void timeout(String orderId);
}
