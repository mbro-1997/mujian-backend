package com.mujian.backend.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.Set;
import org.slf4j.*;

/** 不打印异常message/SQL/URL/正文；只保留类型、代码位置和关联标识。 */
public final class FailureDiagnostics {
  private static final Logger LOG = LoggerFactory.getLogger(FailureDiagnostics.class);
  private static final String LOGGED = FailureDiagnostics.class.getName() + ".logged";

  private FailureDiagnostics() {}

  /** 提取可用于定位的应用代码位置。 */
  public static String locations(Throwable error) {
    StringBuilder chain = new StringBuilder();
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
      if (!chain.isEmpty()) chain.append(" <- ");
      chain.append(t.getClass().getName());
      int count = 0;
      for (StackTraceElement frame : t.getStackTrace()) {
        if (frame.getClassName().startsWith("com.mujian.") || count == 0) {
          chain.append(" at ").append(frame);
          if (++count == 5) break;
        }
      }
      if (seen.size() == 12) break;
    }
    return chain.toString();
  }

  /** 记录请求异常的位置及追踪号。 */
  public static void request(HttpServletRequest request, ApiFailure failure, Throwable error) {
    if (failure.status < 500 || Boolean.TRUE.equals(request.getAttribute(LOGGED))) return;
    request.setAttribute(LOGGED, true);
    LOG.error(
        "event=api_failure traceId={} method={} route={} code={} reason={} causes={}",
        request.getAttribute("traceId"),
        request.getMethod(),
        request.getAttribute("diagnosticRoute"),
        failure.code,
        failure.reason,
        locations(error));
  }

  /** 为后台失败记录脱敏诊断信息。 */
  public static void background(String operation, String runId, Throwable error) {
    LOG.error(
        "event=background_failure operation={} runId={} causes={}",
        operation,
        runId,
        locations(error));
  }
}
