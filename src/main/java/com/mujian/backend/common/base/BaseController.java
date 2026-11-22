package com.mujian.backend.common.base;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;

/** 只封装响应，不提供继承即开放的CRUD或隐藏权限逻辑。 */
public abstract class BaseController {
  /** 将业务结果和同一请求的追踪号封入响应。 */
  protected <T> ApiResponse<T> success(HttpServletRequest request, T data) {
    return new ApiResponse<>(D2Codes.CODE_OK, "成功", data, (String) request.getAttribute("traceId"));
  }
}
