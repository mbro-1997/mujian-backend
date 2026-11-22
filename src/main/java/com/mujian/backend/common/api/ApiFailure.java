package com.mujian.backend.common.api;

import com.mujian.backend.common.support.D2Codes;

public final class ApiFailure extends RuntimeException {
  public final int status;
  public final Integer retryAfterSeconds;
  public final String code, reason;

  public ApiFailure(int status, String code, String reason) {
    this(status, code, reason, null);
  }

  public ApiFailure(int status, String code, String reason, Integer retryAfterSeconds) {
    super(reason);
    if (status == 429 && (retryAfterSeconds == null || retryAfterSeconds < 1))
      throw new IllegalArgumentException("429 requires the originating policy wait");
    this.retryAfterSeconds = retryAfterSeconds;
    this.status = status;
    this.code = code;
    this.reason = reason;
  }

  /** 保留原异常以便按请求追踪。 */
  public ApiFailure causedBy(Throwable cause) {
    if (cause != this) initCause(cause);
    return this;
  }

  /** 构造请求参数无效的标准错误。 */
  public static ApiFailure invalid() {
    return new ApiFailure(400, D2Codes.CODE_INVALID_ARGUMENT, D2Codes.CODE_REQUEST_INVALID);
  }

  /** 构造需要重新登录的标准错误。 */
  public static ApiFailure unauthorized() {
    return new ApiFailure(401, D2Codes.CODE_UNAUTHORIZED, D2Codes.CODE_LOGIN_REQUIRED);
  }

  /** 带上具体原因返回权限拒绝。 */
  public static ApiFailure denied(String reason) {
    return new ApiFailure(403, D2Codes.CODE_FORBIDDEN, reason);
  }

  /** 报告账号上下文违反一致性约束。 */
  public static ApiFailure inconsistent() {
    return new ApiFailure(500, D2Codes.CODE_DATA_INTEGRITY_ERROR, D2Codes.CODE_ACCOUNT_CONTEXT_INCONSISTENT);
  }

  /** 报告会话存储不可用。 */
  public static ApiFailure redis() {
    return new ApiFailure(503, D2Codes.CODE_SERVICE_UNAVAILABLE, D2Codes.CODE_SESSION_STORE_UNAVAILABLE);
  }
}
