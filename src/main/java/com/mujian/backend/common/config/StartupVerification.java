package com.mujian.backend.common.config;

import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.account.mapper.AccountMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StartupVerification implements ApplicationRunner {
  private final AccountMapper accountMapper;
  private final Environment env;
  private final JdbcTemplate jdbc;

  public StartupVerification(AccountMapper u, Environment e, JdbcTemplate j) {
    accountMapper = u;
    env = e;
    jdbc = j;
  }

  /** 启动时检查环境与安全配置是否一致。 */
  public void run(ApplicationArguments args) {
    if (java.util.Arrays.asList(env.getActiveProfiles()).contains("local")
        && (java.util.Arrays.asList(env.getActiveProfiles()).contains("prod")
            || java.util.Arrays.asList(env.getActiveProfiles()).contains("production")))
      throw new IllegalStateException("local/prod profiles are mutually exclusive");
    if ("root".equalsIgnoreCase(env.getProperty("spring.datasource.username", "")))
      throw new IllegalStateException("Application must not run as root");
    String mode = env.getProperty("mj.environment", "production");
    if (java.util.Arrays.asList(env.getActiveProfiles()).contains("production")
        && !"production".equals(mode)) throw new IllegalStateException("生产配置禁止开发 Cookie 放宽");
    if ("production".equals(mode)) {
      for (String key :
          java.util.List.of(
              "mj.wechat.app-id",
              "mj.wechat.app-secret",
              "mj.cos.secret-id",
              "mj.cos.secret-key",
              "mj.verification.crypto.active-version"))
        if (env.getProperty(key, "").isBlank())
          throw new IllegalStateException("CONFIG_ERROR: missing " + key);
      String version = env.getProperty("mj.verification.crypto.active-version");
      try {
        if (java.util.Base64.getDecoder()
                .decode(env.getProperty("mj.verification.crypto.keys." + version, ""))
                .length
            != 32) throw new IllegalArgumentException();
      } catch (Exception e) {
        throw new IllegalStateException("CONFIG_ERROR: verification key must be 32 bytes");
      }
    }
    if ("test".equals(mode)
        && java.util.Arrays.asList(env.getActiveProfiles()).contains("compatibility")) return;
    if (!jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8.4."))
      throw new IllegalStateException("需要 MySQL 8.4");
    if (accountMapper.countLoginIdentitiesByUserId(SystemIdentity.SYSTEM_USER_ID) > 0) throw new IllegalStateException("CONFIG_ERROR: system login identity");
    if (accountMapper.countInvalidSystemIdentity(SystemIdentity.SYSTEM_USER_ID) != 0)
      throw new IllegalStateException("CONFIG_ERROR: 系统身份缺失或被授予登录能力");
  }
}
