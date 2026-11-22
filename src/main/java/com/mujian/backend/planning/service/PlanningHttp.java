package com.mujian.backend.planning.service;

import com.mujian.backend.common.enums.PlanningOperation;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.planning.dto.PlanningCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** HTTP适配只读取统一边界已验证的身份和命令摘要。 */
@Component
public class PlanningHttp {
  private final SessionService sessionService;

  /** 复用已有Bearer会话。 */
  public PlanningHttp(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  /** 获取当前会话用户。 */
  public String actor(HttpServletRequest httpServletRequestRequest) {
    return sessionService.user(sessionService.from(httpServletRequestRequest));
  }

  /** 不接受客户端提交owner，摘要由统一过滤器提供。 */
  public PlanningCommand command(
      HttpServletRequest httpServletRequestRequest, PlanningOperation planningOperationOperation) {
    String hash = (String) httpServletRequestRequest.getAttribute("planningHash");
    if (hash == null) throw new IllegalStateException("missing validated planning digest");
    return new PlanningCommand(
        actor(httpServletRequestRequest),
        httpServletRequestRequest.getHeader("Idempotency-Key"),
        planningOperationOperation,
        hash);
  }
}
