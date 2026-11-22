package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.application.service.admin.AdminInitializationService;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import java.nio.file.*;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import tools.jackson.databind.JsonNode;

/** 仅测试classpath；真实HTTP/MySQL/Redis，虚构微信/COS。不得打入正式JAR。 */
public final class D2BVisualFixturesCli {
  /** 启动本测试命令行入口。 */
  public static void main(String[] args) throws Exception {
    D2BApplicationTest d2BApplicationTest = new D2BApplicationTest();
    d2BApplicationTest.start();
    Runtime.getRuntime().addShutdownHook(new Thread(d2BApplicationTest::stop));
    D2ApplicationTest d2ApplicationTest = d2BApplicationTest.f;
    String username = "b.visual." + Ids.next(), password = UUID.randomUUID().toString();
    d2ApplicationTest.app.getBean(AdminInitializationService.class).initialize(username, password);
    User pending = d2BApplicationTest.user();
    JsonNode application = d2BApplicationTest.onboard(pending);
    User owner = d2BApplicationTest.user();
    JsonNode accepted = d2BApplicationTest.onboard(owner);
    String factory = d2BApplicationTest.approve(owner, accepted);
    JsonNode profile = d2BApplicationTest.call("GET", "/api/me/factory-profile", null, owner, null, 200);
    Map<String, Object> fields = d2BApplicationTest.existing(profile.path("materials"));
    fields.put("factoryName", "灯影舞美 · 隔离审核样本");
    fields.put("remark", "隔离测试资料变更，等待审核后生效");
    JsonNode change =
        d2BApplicationTest.call(
            "POST",
            "/api/me/factory-profile-changes",
            view(
                "previousApplicationId",
                null,
                "baseProfileVersion",
                "1",
                "phoneVerificationProof",
                null,
                "proposed",
                fields),
            owner,
            Ids.next(),
            200);
    if (Arrays.asList(args).contains("--review-incremental")) {
      d2ApplicationTest.db.update(
          "UPDATE factory_profile SET profile_version=2,factory_name='灯影舞美 ·"
              + " 当前新版',factory_status='suspended' WHERE id=?",
          factory);
    }
    Path secret = Path.of(".local/test/d2b-visual-runtime.json");
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
                "applicationId",
                application.path("id").asString(),
                "changeId",
                change.path("application").path("id").asString(),
                "factoryId",
                factory,
                "boundary",
                "isolated MySQL/Redis; fake WeChat/COS")));
    Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"));
    System.out.println(
        "B visual fixture ready " + d2ApplicationTest.base + " configuration .local/test/d2b-visual-runtime.json");
    new CountDownLatch(1).await();
  }
}
