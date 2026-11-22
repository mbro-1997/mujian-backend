package com.mujian.backend.common.exception;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.api.ApiFailure;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionAdvice {
  /** 开始处理已提交的申请。 */
  @ExceptionHandler(Exception.class)
  public Object handle(Exception e, HttpServletRequest r, HttpServletResponse response) {
    ApiFailure apiFailure = FailureResolver.resolve(e);
    FailureDiagnostics.request(r, apiFailure, e);
    if (e instanceof org.springframework.web.HttpRequestMethodNotSupportedException method
        && method.getSupportedMethods() != null)
      response.setHeader("Allow", String.join(", ", method.getSupportedMethods()));
    if (response.isCommitted()) return null;
    response.setHeader("Content-Length", null);
    response.setHeader("Content-Disposition", null);
    response.setContentType("application/json");
    response.setStatus(apiFailure.status);
    if (apiFailure.status == 429) response.setHeader("Retry-After", apiFailure.retryAfterSeconds.toString());
    return ApiReply.error(r, apiFailure);
  }
}
