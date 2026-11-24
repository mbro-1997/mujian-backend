package com.mujian.backend.common.config;

import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Mac local 启动时让 COS 直连，保留 JVM 原有代理例外。 */
@Configuration(proxyBeanMethods = false)
@Profile("local & !test & !prod & !production")
public class LocalCosNetworkConfiguration {
  /** 在客户端实例化前补齐 HTTP 和 SOCKS 两种代理的例外。 */
  @Bean
  static BeanFactoryPostProcessor cosLocalProxyExceptions(Environment environment) {
    return beanFactory -> {
      if (!System.getProperty("os.name", "").startsWith("Mac")
          || !environment.getProperty("mj.cos.proxy.host", "").isBlank()) return;
      String host = environment.getRequiredProperty("mj.cos.bucket") + ".cos."
          + environment.getRequiredProperty("mj.cos.region") + ".myqcloud.com";
      appendHost("http.nonProxyHosts", host);
      appendHost("socksNonProxyHosts", host);
    };
  }

  /** 保留顺序、去重并追加本环境默认桶域名。 */
  private static void appendHost(String property, String host) {
    Set<String> hosts = new LinkedHashSet<>(
        Arrays.asList(System.getProperty(property, "").split("\\|")));
    hosts.remove("");
    hosts.add(host);
    System.setProperty(property, String.join("|", hosts));
  }
}
