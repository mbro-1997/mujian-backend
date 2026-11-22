package com.mujian.backend.d2a;

import java.io.InputStream;
import tools.jackson.databind.JsonNode;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.delivery.service.OrderEvidence;
import com.mujian.backend.infrastructure.storage.CosMaterialStore;
import com.mujian.backend.material.service.VerificationMaterials;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

/** 显式开启的隔离联调，真实存储只写本次生成的订单材料，授权、订单和退款仍是测试身份。 */
@EnabledIfEnvironmentVariable(named = "MJ_D4_COS_PROBE", matches = "1")
class D4CosEvidenceTest {
  /** 验证真实订单私有照片。 */
  @Test
  void actualPrivateOrderPhoto() throws Exception {
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(Path.of(".local/runtime.properties"))) {
      properties.load(in);
    }
    MockEnvironment env = new MockEnvironment();
    for (String k : List.of("mj.cos.secret-id", "mj.cos.secret-key"))
      env.setProperty(k, properties.getProperty(k));
    env.setProperty("mj.verification.cleanup.cloud-policy-verified", "true");
    CosMaterialStore store = new CosMaterialStore(env);
    D3BQuoteTest d3BQuoteTest = new D4RecoveryHttpTest().fixture();
    String object = null, version = null;
    try {
      d3BQuoteTest.start();
      String order = d3BQuoteTest.paidOrder(), path = "/api/me/factory-orders/" + order;
      d3BQuoteTest.call("POST", path + "/accept", view("expectedVersion", 0), d3BQuoteTest.supplier, d3BQuoteTest.key(), 200);
      ReflectionTestUtils.setField(
          d3BQuoteTest.f.app.getBean(VerificationMaterials.class), "privateMaterialStore", store);
      ReflectionTestUtils.setField(
          d3BQuoteTest.f.app.getBean(OrderEvidence.class), "privateMaterialStore", store);
      String receipt = D4EvidenceHttpTest.upload(d3BQuoteTest, path, "outbound", d3BQuoteTest.supplier, 200);
      object = VerificationMaterials.key(d3BQuoteTest.supplier.id(), "order-evidence", receipt);
      version = store.head(object).versionId();
      d3BQuoteTest.call(
          "POST",
          path + "/evidence",
          view(
              "expectedVersion",
              0,
              "materials",
              List.of(D4EvidenceHttpTest.part(receipt, "outbound"))),
          d3BQuoteTest.supplier,
          d3BQuoteTest.key(),
          200);
      String id =
          d3BQuoteTest.call("GET", path + "/evidence", null, d3BQuoteTest.supplier, null, 200)
              .path("items")
              .get(0)
              .path("id")
              .asString();
      JsonNode signed =
          d3BQuoteTest.call("POST", path + "/evidence/" + id + "/view", Map.of(), d3BQuoteTest.supplier, null, 200);
      URI uri = URI.create(signed.path("url").asString());
      assertEquals("https", uri.getScheme());
      HttpResponse<byte[]> response =
          d3BQuoteTest.f.http.send(
              HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
      assertEquals(200, response.statusCode());
      assertArrayEquals(D2ApplicationTest.png(), response.body());
      URI unsigned = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
      assertEquals(
          403,
          d3BQuoteTest.f
              .http
              .send(
                  HttpRequest.newBuilder(unsigned).GET().build(),
                  HttpResponse.BodyHandlers.discarding())
              .statusCode());
    } finally {
      try {
        new D4OutboundHttpTest().cleanup(d3BQuoteTest);
      } finally {
        try {
          if (object != null) store.delete(object, version);
        } finally {
          store.close();
        }
      }
    }
  }
}
