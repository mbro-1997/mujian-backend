package com.mujian.backend.d2a;

import com.mujian.backend.application.service.admin.AdminInitializationService;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import java.nio.file.*;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import tools.jackson.databind.JsonNode;

/** Test classpath only: the formal frontend can connect to this isolated fixture process. */
public final class D2VisualFixturesCli {
  /** 启动本测试命令行入口。 */
  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--rules-final")) { rulesFinal(); return; }
    if (args.length == 1 && args[0].equals("--remaining")) { remaining(); return; }
    D2ApplicationTest fixture = new D2ApplicationTest();
    int port = args.length == 0 ? 0 : Integer.parseInt(args[0]);
    if (port != 0 && port != 18080)
      throw new IllegalArgumentException("Only random or local UI test port allowed");
    fixture.start(port);
    Runtime.getRuntime().addShutdownHook(new Thread(fixture::stop));
    String username = "d2.visual." + Ids.next(), password = UUID.randomUUID().toString();
    fixture.app.getBean(AdminInitializationService.class).initialize(username, password);
    String otherUsername="d2.other."+Ids.next(),otherPassword=UUID.randomUUID().toString();
    fixture.app.getBean(AdminInitializationService.class).initialize(otherUsername,otherPassword);
    Path otherSecret=Path.of(".local/test/visual-other-admin.json");
    Files.writeString(otherSecret,fixture.json.writeValueAsString(Map.of("username",otherUsername,"password",otherPassword)));
    Files.setPosixFilePermissions(otherSecret,PosixFilePermissions.fromString("rw-------"));
    User user = fixture.user();
    User emptyUser = fixture.user();
    LinkedHashMap<String, String> applications = new LinkedHashMap<String, String>();
    for (String domain : List.of("student", "realname")) {
      JsonNode result =
          fixture.call(
              "POST",
              "/api/me/" + domain + "-verifications",
              fixture.body(user, domain),
              user,
              Ids.next(),
              200);
      applications.put(domain, result.path("application").path("id").asString());
    }
    Path directory = Path.of(".local/test");
    Path secret = directory.resolve("visual-runtime.json");
    Files.writeString(
        secret,
        fixture.json.writeValueAsString(
            Map.of(
                "baseUrl",
                fixture.base,
                "username",
                username,
                "password",
                password,
                "userId",
                user.id(),
                "miniToken",
                user.token(),
                "emptyUserId",
                emptyUser.id(),
                "emptyMiniToken",
                emptyUser.token(),
                "applications",
                applications,
                "boundary",
                "isolated MySQL/Redis; fake WeChat/COS")),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
    Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"));
    System.out.println(
        "D2 visual fixture ready: "
            + fixture.base
            + "; configuration: .local/test/visual-runtime.json; isolated MySQL/Redis; fake"
            + " WeChat/COS.");
    new CountDownLatch(1).await();
  }
  // Explicit remaining-native workflow: reuse existing identities and rejected application.
  /** 读取当前业务剩余量。 */
  static void remaining() throws Exception {
    D2ApplicationTest fixture = new D2ApplicationTest();
    fixture.start(0, "--mj.prop.qr-worker-enabled=false");
    String database = fixture.db.queryForObject("SELECT DATABASE()", String.class);
    if (!database.equals("darkmujian_d1_test_d2a_20261028185351_4ebca4")) {
      fixture.stop(); throw new IllegalStateException("remaining UI requires the original isolated fixture database");
    }
    List<String> users = fixture.db.queryForList("SELECT u.id FROM sys_user u WHERE u.user_status='active' AND u.register_status='demander_bound' AND NOT EXISTS(SELECT 1 FROM user_realname_verification r WHERE r.user_id=u.id) AND NOT EXISTS(SELECT 1 FROM user_student_verification v WHERE v.user_id=u.id) ORDER BY u.id LIMIT 1",String.class);
    if(users.size()!=1)throw new IllegalStateException("no existing empty certification identity");
    String cert=users.get(0), rejected="785d2b65b8f6413bb17acaf5db974dad";
    if(fixture.db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND register_status='factory_rejected' AND user_status='active'",Integer.class,rejected)!=1)throw new IllegalStateException("original rejected fixture no longer eligible");
    String certToken=fixture.sessions.create(cert,"miniapp").session().getId();
    String factoryToken=fixture.sessions.create(rejected,"miniapp").session().getId();
    Path secret=Path.of(".local/test/remaining-native-runtime.json");
    Files.writeString(secret,fixture.json.writeValueAsString(Map.of("baseUrl",fixture.base,"certUser",cert,"certToken",certToken,"factoryUser",rejected,"factoryToken",factoryToken,"adminCookie",fixture.adminCookie,"csrf",fixture.csrf,"boundary","isolated MySQL/Redis; fake WeChat/COS","database",database)));
    Files.setPosixFilePermissions(secret,PosixFilePermissions.fromString("rw-------"));
    // ContextClosedEvent precedes Redis bean destruction; a competing JVM hook does not.
    fixture.app.addApplicationListener((org.springframework.context.event.ContextClosedEvent event) -> {
      fixture.sessions.revoke(certToken);
      fixture.sessions.revoke(factoryToken);
      fixture.sessions.revoke(fixture.adminCookie.substring("mj_admin_dev=".length()));
    });
    System.out.println("remaining native fixture ready; existing identities; secret .local/test/remaining-native-runtime.json");
    new CountDownLatch(1).await();
  }

  // One native final-button attempt, using another existing rejected fixture only.
  /** 发布用例所需规则。 */
  static void rulesFinal() throws Exception {
    D2ApplicationTest fixture=new D2ApplicationTest();
    fixture.start(0,"--server.address=127.0.0.1","--mj.prop.qr-worker-enabled=false","--mj.verification.cleanup.enabled=false");
    fixture.app.addApplicationListener((org.springframework.context.event.ContextClosedEvent event)->
      fixture.sessions.revoke(fixture.adminCookie.substring("mj_admin_dev=".length())));
    String owner="5d8d66b89c794b24b49d6ebe4d57fcc1", previous="315ed101c4e1450291e4c0f474421e91";
    String database=fixture.db.queryForObject("SELECT DATABASE()",String.class);
    if(!"darkmujian_d1_test_d2a_20261028185351_4ebca4".equals(database)
       || fixture.db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND user_status='active' AND register_status='factory_rejected'",Integer.class,owner)!=1
       || fixture.db.queryForObject("SELECT COUNT(*) FROM factory_onboarding_audit WHERE id=? AND applicant_user_id=? AND audit_status='rejected'",Integer.class,previous,owner)!=1) {
      fixture.stop();throw new IllegalStateException("existing rejected isolated fixture no longer eligible");
    }
    String token=fixture.sessions.create(owner,"miniapp").session().getId();
    Path secret=Path.of(".local/test/rules-final-runtime.json");
    Files.writeString(secret,fixture.json.writeValueAsString(Map.of("baseUrl",fixture.base,"factoryUser",owner,"factoryToken",token,"previous",previous,"database",database)));
    Files.setPosixFilePermissions(secret,PosixFilePermissions.fromString("rw-------"));
    fixture.app.addApplicationListener((org.springframework.context.event.ContextClosedEvent event)->{
      fixture.sessions.revoke(token);
    });
    System.out.println("rules-final isolated fixture ready; credentials in private file");
    new CountDownLatch(1).await();
  }

}
