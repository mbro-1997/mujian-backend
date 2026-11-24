package com.mujian.backend.common.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class LocalCosNetworkConfigurationTest {
  /** Mac local 追加域名且不破坏旧例外；重复加载、云端及显式代理不产生额外修改。 */
  @Test
  void appendOnlyForLocalMacAndPreserveExistingExceptions() {
    Map<String, String> original = new HashMap<>();
    for (String name : new String[] {"os.name", "http.nonProxyHosts", "socksNonProxyHosts"}) {
      original.put(name, System.getProperty(name));
    }
    try {
      System.setProperty("os.name", "Mac OS X");
      System.setProperty("http.nonProxyHosts", "localhost|*.internal");
      System.setProperty("socksNonProxyHosts", "127.*|*.private");
      load("production", "");
      assertEquals("localhost|*.internal", System.getProperty("http.nonProxyHosts"));
      load("local", "egress.example.test");
      assertEquals("127.*|*.private", System.getProperty("socksNonProxyHosts"));
      load("local", "");
      load("local", "");
      String domain = "cct1-1429809468.cos.ap-shanghai.myqcloud.com";
      assertEquals("localhost|*.internal|" + domain, System.getProperty("http.nonProxyHosts"));
      assertEquals("127.*|*.private|" + domain, System.getProperty("socksNonProxyHosts"));
      System.setProperty("os.name", "Linux");
      System.setProperty("http.nonProxyHosts", "localhost");
      load("local", "");
      assertEquals("localhost", System.getProperty("http.nonProxyHosts"));
    } finally {
      original.forEach((name, value) -> {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
      });
    }
  }

  /** 仅加载网络配置类，不启动数据库或业务任务。 */
  private void load(String profile, String proxyHost) {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("mj.cos.bucket", "cct1-1429809468")
        .withProperty("mj.cos.region", "ap-shanghai")
        .withProperty("mj.cos.proxy.host", proxyHost);
    environment.setActiveProfiles(profile);
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.setEnvironment(environment);
      context.register(LocalCosNetworkConfiguration.class);
      context.refresh();
    }
  }
}
