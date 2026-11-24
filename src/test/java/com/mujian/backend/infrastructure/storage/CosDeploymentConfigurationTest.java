package com.mujian.backend.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.http.HttpProtocol;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CosDeploymentConfigurationTest {
  /** 同一实现从环境读取桶和地域，签名不需要联网或实际凭据。 */
  @Test
  void externalBucketAndRegionDetermineHttpsDomain() {
    for (String region : new String[] {"ap-shanghai", "ap-guangzhou"}) {
      MockEnvironment environment = environment().withProperty("mj.cos.region", region);
      CosMaterialStore cosMaterialStore = new CosMaterialStore(environment);
      try {
        URI uri = URI.create(cosMaterialStore.sign(
            "dev/d2-c/props/" + "a".repeat(32) + "/" + "b".repeat(32),
            Instant.now().plusSeconds(120)));
        assertEquals("https", uri.getScheme());
        assertEquals("deployment-test-1234567890.cos." + region + ".myqcloud.com", uri.getHost());
        ClientConfig clientConfig = cosMaterialStore.clientConfig();
        assertEquals(HttpProtocol.https, clientConfig.getHttpProtocol());
        assertTrue(clientConfig.getHttpProxyIp() == null || clientConfig.getHttpProxyIp().isBlank());
      } finally {
        cosMaterialStore.close();
      }
    }
  }

  /** 仅显式配置出口代理时设置 SDK 代理，支持代理域名和认证参数。 */
  @Test
  void optionalProxyUsesOnlyEnvironmentSettings() {
    MockEnvironment environment = environment()
        .withProperty("mj.cos.proxy.host", "egress.example.test")
        .withProperty("mj.cos.proxy.port", "3128")
        .withProperty("mj.cos.proxy.username", "test-user")
        .withProperty("mj.cos.proxy.password", "test-password");
    ClientConfig clientConfig = new CosMaterialStore(environment).clientConfig();
    assertEquals("egress.example.test", clientConfig.getHttpProxyIp());
    assertEquals(3128, clientConfig.getHttpProxyPort());
    assertEquals("test-user", clientConfig.getProxyUsername());
    assertEquals("test-password", clientConfig.getProxyPassword());
  }

  /** 不完整的代理配置明确失败，避免误以为已启用出口代理。 */
  @Test
  void incompleteProxyConfigurationFails() {
    assertThrows(IllegalArgumentException.class, () -> new CosMaterialStore(
        environment().withProperty("mj.cos.proxy.host", "egress.example.test")).clientConfig());
    assertThrows(IllegalArgumentException.class, () -> new CosMaterialStore(
        environment().withProperty("mj.cos.proxy.port", "3128")).clientConfig());
  }

  /** 创建无真实凭据的配置。 */
  private MockEnvironment environment() {
    return new MockEnvironment()
        .withProperty("mj.cos.bucket", "deployment-test-1234567890")
        .withProperty("mj.cos.region", "ap-shanghai")
        .withProperty("mj.cos.secret-id", "example-id")
        .withProperty("mj.cos.secret-key", "example-key");
  }
}
