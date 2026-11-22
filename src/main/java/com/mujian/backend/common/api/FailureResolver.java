package com.mujian.backend.common.api;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import java.util.*;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.dao.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.*;

/** 通道不可用与程序SQL错误分开；业务唯一约束由所属领域映射明确原因。 */
public final class FailureResolver {
  private FailureResolver() {}

  /** 按当前事实确定处理结果。 */
  public static ApiFailure resolve(Throwable error) {
    ArrayList<Throwable> chain = new ArrayList<Throwable>();
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
      if (t instanceof ApiFailure f) return f;
      chain.add(t);
    }
    for (Throwable t : chain) {
      if (t instanceof org.springframework.web.multipart.MaxUploadSizeExceededException)
        return new ApiFailure(413, D2Codes.CODE_PAYLOAD_TOO_LARGE, D2Codes.CODE_PAYLOAD_TOO_LARGE).causedBy(error);
      if (t instanceof HttpRequestMethodNotSupportedException)
        return new ApiFailure(405, BusinessFailureReason.METHOD_NOT_ALLOWED.name(), BusinessFailureReason.METHOD_NOT_ALLOWED.name()).causedBy(error);
    }
    for (Throwable t : chain) {
      if (t instanceof org.springframework.validation.BindException
          || t
              instanceof
              org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
          || t instanceof org.springframework.web.method.annotation.HandlerMethodValidationException
          || t instanceof jakarta.validation.ConstraintViolationException
          || t instanceof MissingServletRequestParameterException
          || t instanceof MissingRequestHeaderException
          || t
              instanceof
              org.springframework.web.multipart.support.MissingServletRequestPartException
          || t instanceof org.springframework.web.multipart.MultipartException
          || t instanceof org.springframework.http.converter.HttpMessageNotReadableException)
        return ApiFailure.invalid().causedBy(error);
    }
    for (Throwable t : chain) {
      if (t instanceof org.springframework.jdbc.BadSqlGrammarException
          || t instanceof DataIntegrityViolationException
          || t instanceof InvalidDataAccessApiUsageException) return internal(error);
    }
    for (Throwable t : chain) {
      if (t instanceof org.springframework.jdbc.CannotGetJdbcConnectionException
          || t instanceof DataAccessResourceFailureException
          || t instanceof TransientDataAccessResourceException
          || t instanceof PessimisticLockingFailureException
          || t instanceof java.sql.SQLTransientConnectionException
          || t instanceof java.sql.SQLNonTransientConnectionException
          || t instanceof java.sql.SQLRecoverableException
          || t instanceof java.sql.SQLException sql
              && sql.getSQLState() != null
              && sql.getSQLState().startsWith("08"))
        return new ApiFailure(503, D2Codes.CODE_SERVICE_UNAVAILABLE, D2Codes.CODE_DATABASE_UNAVAILABLE).causedBy(error);
    }
    // NumberFormatException/任意IllegalArgumentException不能一概伪装成客户端输入错误。
    return internal(error);
  }

  /** 记录内部异常并构造统一错误响应。 */
  private static ApiFailure internal(Throwable cause) {
    return new ApiFailure(500, D2Codes.CODE_INTERNAL_ERROR, D2Codes.CODE_UNEXPECTED_ERROR).causedBy(cause);
  }
}
