package com.mujian.backend.application.service.admin;

import com.mujian.backend.common.enums.BusinessFailureReason;
import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.LoginRateLimiter;
import com.mujian.backend.common.security.LoginRateLimiter.Attempt;
import com.mujian.backend.infrastructure.persistence.mapper.admin.AdminMapper;
import com.mujian.backend.infrastructure.persistence.record.admin.AdminRecord;
import java.time.*;
import java.util.Locale;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminLoginService {
  public record Authenticated(String userId, LoginRateLimiter.Attempt budget)
      implements AutoCloseable {
    /** 将登录限流预算登记为成功。 */
    public void success() {
      budget.success();
    }

    /** 归还未完成登录的限流预算。 */
    @Override
    public void close() {
      budget.close();
    }
  }

  private final AdminCaptchaService adminCaptchaService;
  private final AdminMapper adminMapper;
  private final AdminAccessService adminAccessService;
  private final LoginRateLimiter loginRateLimiter;
  private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder(12);
  private final String dummyHash =
      bCryptPasswordEncoder.encode(java.util.UUID.randomUUID().toString());

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public AdminLoginService(
      AdminCaptchaService adminCaptchaService,
      AdminMapper adminMapper,
      AdminAccessService adminAccessService,
      LoginRateLimiter loginRateLimiter) {
    this.adminCaptchaService=adminCaptchaService;
    this.adminMapper = adminMapper;
    this.adminAccessService = adminAccessService;
    this.loginRateLimiter = loginRateLimiter;
  }

  /** 验证密码与管理员状态，成功后交接原限流预算。 */
  public Authenticated authenticate(String input, String password, String ip,String session,String captchaId,String captchaAnswer) {
    String username = input.trim().toLowerCase(Locale.ROOT);
    Attempt attempt = loginRateLimiter.reserve(username, ip);
    try {
      try { adminCaptchaService.consume(session,captchaId,captchaAnswer); }
      catch(ApiFailure failure) {
        if(failure.status==400)attempt.failure();
        throw failure;
      }
      AdminRecord adminRecord = adminMapper.selectAdminByUsername(username);
      boolean valid =
          bCryptPasswordEncoder.matches(
              password, adminRecord == null ? dummyHash : adminRecord.passwordHash());
      if (adminRecord == null || !valid) {
        attempt.failure();
        throw new ApiFailure(401, CODE_UNAUTHORIZED, BusinessFailureReason.CREDENTIALS_INVALID.name());
      }
      adminAccessService.load(adminRecord.userId());
      adminMapper.updateAdminLoginTime(
          adminRecord.id(), LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
      // 预算交给控制器，在会话创建成功后再结算，异常由 close 归还。
      return new Authenticated(adminRecord.userId(), attempt);
    } catch (RuntimeException failure) {
      try {
        attempt.close();
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }
}
