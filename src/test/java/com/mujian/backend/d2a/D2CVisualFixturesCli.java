package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.application.service.admin.AdminInitializationService;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.prop.service.PropQrImages;
import java.nio.file.*;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import tools.jackson.databind.JsonNode;

/**
 * Test classpath only. Isolated MySQL/Redis; fake WeChat/COS. Never included in application JAR.
 */
public final class D2CVisualFixturesCli {
  /** 启动本测试命令行入口。 */
  public static void main(String[] args) throws Exception {
    D2CApplicationTest d2CApplicationTest = new D2CApplicationTest();
    D2ApplicationTest d2ApplicationTest = d2CApplicationTest.f;
    d2ApplicationTest.start(18081, "--server.address=127.0.0.1", "--mj.prop.qr-worker-enabled=false");
    Runtime.getRuntime().addShutdownHook(new Thread(d2ApplicationTest::stop));
    String username = "c.visual." + Ids.next(), password = UUID.randomUUID().toString();
    d2ApplicationTest.app.getBean(AdminInitializationService.class).initialize(username, password);
    D2CVisualMedia media = new D2CVisualMedia(d2ApplicationTest.store);
    Runtime.getRuntime().addShutdownHook(new Thread(media::close));
    User owner = d2CApplicationTest.supplier();
    User demander = d2ApplicationTest.user();
    LinkedHashMap<String, Object> ids = new LinkedHashMap<String, Object>();
    for (String type : List.of("A_ASSET", "B_CONSUMABLE")) {
      Map<String, Object> fields = d2CApplicationTest.fields(owner);
      fields.put("propName", type.equals("A_ASSET") ? "复古雕花实木椅 · 隔离采证" : "舞美仿真花束 · 隔离采证");
      JsonNode created =
          d2CApplicationTest.call(
              "POST",
              "/api/me/factory-props",
              view(
                  "assetType",
                  type,
                  "fields",
                  fields,
                  "initialQuantity",
                  type.equals("A_ASSET") ? 3 : 25,
                  "applyRemark",
                  "C 批隔离界面采证"),
              owner,
              Ids.next(),
              200);
      ids.put(type, created.path("skuId").asString());
      d2CApplicationTest.approve("sku_audit", created.path("targetId").asString(), 0);
    }
    JsonNode pending = d2CApplicationTest.create(owner, "A_ASSET", 2);
    ids.put("pendingReview", pending.path("targetId").asString());
    d2ApplicationTest.wx.delegate = d2CApplicationTest.pngGateway();
    try {
      for (String id :
          d2ApplicationTest.db.queryForList(
              "SELECT q.id FROM prop_qr_code q JOIN prop_sku s ON s.id=q.sku_id JOIN"
                  + " factory_profile f ON f.id=s.factory_id WHERE f.owner_user_id=?",
              String.class,
              owner.id())) d2ApplicationTest.app.getBean(PropQrImages.class).generate(id);
    } finally {
      d2ApplicationTest.wx.delegate = null;
    }
    Path secret = Path.of(".local/test/d2c-visual-runtime.json");
    Files.writeString(
        secret,
        d2ApplicationTest.json.writeValueAsString(
            view(
                "baseUrl",
                d2ApplicationTest.base,
                "username",
                username,
                "password",
                password,
                "ownerToken",
                owner.token(),
                "demanderToken",
                demander.token(),
                "ids",
                ids,
                "boundary",
                "isolated MySQL/Redis; fake WeChat/COS")));
    Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"));
    System.out.println(
        "C visual fixture ready: " + d2ApplicationTest.base + "; .local/test/d2c-visual-runtime.json");
    new CountDownLatch(1).await();
  }
}
