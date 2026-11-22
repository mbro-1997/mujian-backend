package com.mujian.backend.common.security;

import com.mujian.backend.common.security.SessionService.Handle;
import jakarta.servlet.http.*;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.Component;

@Component
public class SessionCsrfRepository implements CsrfTokenRepository {
  private final SessionService sessionService;

  public SessionCsrfRepository(SessionService s) {
    sessionService = s;
  }

  /** 为会话生成新的防伪令牌。 */
  public CsrfToken generateToken(HttpServletRequest r) {
    return new HttpSessionCsrfTokenRepository().generateToken(r);
  }

  /** 取回本次请求所属会话的令牌。 */
  public CsrfToken loadToken(HttpServletRequest r) {
    Handle handle = sessionService.from(r);
    return handle == null ? null : handle.session().getAttribute("csrf");
  }

  /** 保存或移除当前会话的令牌。 */
  public void saveToken(CsrfToken token, HttpServletRequest r, HttpServletResponse response) {
    // 每次新会话已预生成 CSRF；已发布会话不可再次保存旧快照。登录轮换创建新的会话与令牌。
    if (token != null && loadToken(r) == null)
      throw com.mujian.backend.common.api.ApiFailure.redis();
  }
}
