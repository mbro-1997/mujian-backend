package com.mujian.backend.common.api;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.enums.FactoryReasonCode;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.LinkedHashMap;

public final class ApiReply {
  private ApiReply() {}

  /** 按统一协议封装成功结果。 */
  public static Map<String, Object> ok(HttpServletRequest r, Object data) {
    LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
    m.put("code", D2Codes.CODE_OK);
    m.put("message", "成功");
    m.put("data", data);
    m.put("traceId", r.getAttribute("traceId"));
    return m;
  }

  /** 按统一协议封装失败原因及追踪编号。 */
  public static Map<String, Object> error(HttpServletRequest r, ApiFailure apiFailure) {
    LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
    m.put(
        "code",
        Boolean.TRUE.equals(r.getAttribute("factoryApi")) && (apiFailure.status == 413 || apiFailure.status == 415)
            ? D2Codes.CODE_INVALID_ARGUMENT
            : apiFailure.code);
    m.put(
        "message",
        FactoryReasonCode.PHONE_VERIFICATION_NOT_CONFIGURED.name().equals(apiFailure.reason) ? "联系电话更换暂未开放，可继续修改其他资料" : apiFailure.reason);
    m.put("data", null);
    m.put("reasonCode", apiFailure.reason);
    if (apiFailure.status == 429) m.put("retryAfterSeconds", apiFailure.retryAfterSeconds);
    m.put("traceId", r.getAttribute("traceId"));
    return m;
  }
}
