package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

/** Only maintenance boundaries; existing B/C helpers provide isolated realistic data. */
class LocalIdentityMaintenanceTest {
  /** 验证纯函数边界及受保护备份。 */
  @Test void pureBoundariesAndSecureBackups() throws Exception {
    Process p = new ProcessBuilder("python3", "deploy/tests/test_identity_maintenance.py").inheritIO().start();
    assertTrue(p.waitFor(30, TimeUnit.SECONDS));
    assertEquals(0, p.exitValue());
  }

  /** 定位待验证的原始操作。 */
  void operation(String action, String user) throws Exception {
    Process p = new ProcessBuilder("python3", "deploy/tests/identity_scenario.py", action, user).inheritIO().start();
    assertTrue(p.waitFor(60, TimeUnit.SECONDS));
    assertEquals(0, p.exitValue(), "maintenance operation " + action);
  }

  /** 验证隔离初始化、回滚、会话往返与权限保留。 */
  @Test void isolatedBootstrapRollbackRoundTripSessionsAndUnchangedPermissions() throws Exception {
    D2CApplicationTest d2CApplicationTest = new D2CApplicationTest();
    d2CApplicationTest.start();
    try {
      D2ApplicationTest d2ApplicationTest = d2CApplicationTest.f;
      SessionService sessions = d2ApplicationTest.app.getBean(SessionService.class);
      User user = d2ApplicationTest.user();
      User other = d2ApplicationTest.user();
      operation("bootstrap", user.id());
      d2CApplicationTest.call("GET", "/api/me/context", null, user, null, 401);
      d2CApplicationTest.call("GET", "/api/me/context", null, other, null, 200);
      operation("rollback-bootstrap", user.id());
      operation("bootstrap", user.id());
      user = new D2ApplicationTest.User(user.id(), sessions.create(user.id(), "miniapp").session().getId());
      assertEquals("SELECT_IDENTITY", d2CApplicationTest.call("GET", "/api/me/context", null, user, null, 200).path("nextAction").asString());
      d2CApplicationTest.b.approve(user, d2CApplicationTest.b.onboard(user)); // Required genuine service approval, not inserted factory rows.
      JsonNode made = d2CApplicationTest.create(user, "B_CONSUMABLE", 3);
      d2CApplicationTest.approve("sku_audit", made.path("targetId").asString(), 0);
      // QR generation is asynchronous; finish the isolated stub image before maintenance.
      JsonNode owned = d2CApplicationTest.call("GET", "/api/me/factory-props/" + made.path("skuId").asString(), null, user, null, 200);
      d2ApplicationTest.app.getBean(com.mujian.backend.prop.service.PropQrImages.class)
          .generate(owned.path("currentQrId").asString());
      operation("forced-rollback", user.id());
      operation("supplier", user.id()); // same-role idempotence preserves current session
      d2CApplicationTest.call("GET", "/api/me/context", null, user, null, 200);
      operation("demander", user.id());
      d2CApplicationTest.call("GET", "/api/me/context", null, user, null, 401);
      user = new D2ApplicationTest.User(user.id(), sessions.create(user.id(), "miniapp").session().getId());
      assertEquals("demander", d2CApplicationTest.call("GET", "/api/me/context", null, user, null, 200).path("roleCode").asString());
      d2CApplicationTest.call("GET", "/api/me/factory-props/options", null, user, null, 403);
      operation("supplier", user.id());
      d2CApplicationTest.call("GET", "/api/me/context", null, user, null, 401);
      user = new D2ApplicationTest.User(user.id(), sessions.create(user.id(), "miniapp").session().getId());
      assertEquals("supplier", d2CApplicationTest.call("GET", "/api/me/context", null, user, null, 200).path("roleCode").asString());
      d2CApplicationTest.call("GET", "/api/me/factory-props/" + made.path("skuId").asString(), null, user, null, 200);
      d2CApplicationTest.call("GET", "/api/me/context", null, other, null, 200);
      // Current admin cookie still works; no global Redis wipe or admin logout.
      d2CApplicationTest.call("GET", "/api/admin/prop-reviews?auditStatus=approved", null, null, null, 200);
    } finally { d2CApplicationTest.stop(); }
  }
}
