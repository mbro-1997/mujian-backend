package com.mujian.backend.order.service;

import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.order.dto.TradingCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** 交易控制器只读取已验证的会话与规范摘要。 */
@Component
public class TradingHttp {
  private final SessionService sessionService;

  /** 复用现有Bearer及管理员会话。 */
  public TradingHttp(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  /** 读取会话中的用户，禁止客户端指定actor。 */
  public String actor(HttpServletRequest request) {
    return sessionService.user(sessionService.from(request));
  }

  /** 取得公共请求边界保存的命令。 */
  public TradingCommand command(
      HttpServletRequest request, TradingOperation tradingOperationOperation) {
    String hash = (String) request.getAttribute("tradingHash");
    if (hash == null) throw new IllegalStateException("missing validated trading digest");
    return new TradingCommand(
        actor(request), request.getHeader("Idempotency-Key"), tradingOperationOperation, hash);
  }
}
