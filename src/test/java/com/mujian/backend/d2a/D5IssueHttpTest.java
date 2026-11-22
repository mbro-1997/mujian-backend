package com.mujian.backend.d2a;

import java.io.OutputStream;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import static com.mujian.backend.common.support.D2Values.view;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.delivery.service.FulfillmentRecovery;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class D5IssueHttpTest {
  /** 两个本人问题使用当前核验版本，后到的问题不能重置已保存的剩余时长。 */
  @Test
  void overlappingPausesPreserveRemainingHours() throws Exception {
    D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    D3BQuoteTest fixture = setup.fixture();
    fixture.extraSources = new Class<?>[0];
    try {
      fixture.start();
      String order = setup.dispatch(fixture);
      String path = "/api/me/rental-orders/" + order + "/issues";
      JdbcTemplate db = fixture.f.db;
      String item = db.queryForObject("SELECT id FROM rental_order_item WHERE rental_order_id=?", String.class, order);
      LocalDateTime deadline = db.queryForObject("SELECT deadline_at FROM order_receipt_check WHERE rental_order_id=?", LocalDateTime.class, order);
      fixture.f.clock.at = deadline.atZone(ZoneId.of("Asia/Shanghai")).toInstant().minus(Duration.ofHours(3));
      fixture.f.sessions.revoke(fixture.f.adminCookie.substring("mj_admin_dev=".length()));
      fixture.f.adminCookie = "mj_admin_dev="+fixture.f.sessions.create(fixture.f.admin,"admin").session().getId();
      fixture.f.csrf = fixture.f.call("GET","/api/admin/auth/csrf",null,null,null,200).path("csrfToken").asString();
      Map<String,Object> body = view("expectedVersion", 0, "scene", "receipt", "effect", "paused",
          "primaryIssueType", "damaged", "summary", "收货时发现道具破损",
          "items", List.of(view("itemId", item, "quantity", 1, "issueType", "damaged", "description", "外壳开裂")),
          "evidence", view("evidenceIds", List.of(), "uploadReceiptIds", List.of()));
      fixture.call("POST",path,body,fixture.f.user(),fixture.key(),404);
      String key = fixture.key();
      JsonNode first = fixture.call("POST",path,body,fixture.buyer,key,200);
      assertEquals(first.path("resourceId"),fixture.call("POST",path,body,fixture.buyer,key,200).path("resourceId"));
      String issueId = first.path("resourceId").asString();
      String myIssue = "/api/me/issues/"+issueId, adminIssue = "/api/admin/issues/"+issueId;
      JsonNode detail = fixture.call("GET",myIssue,null,fixture.buyer,null,200);
      assertEquals("damaged",detail.path("items").get(0).path("issueType").asString());
      assertEquals(order,detail.path("orderId").asString());
      assertEquals(issueId,fixture.call("GET","/api/me/issues?orderId="+order,null,fixture.buyer,null,200).path("items").get(0).path("id").asString());
      fixture.call("GET",myIssue,null,fixture.f.user(),null,404);
      assertEquals("request_evidence",fixture.call("GET",adminIssue,null,null,null,200).path("availableActions").get(0).asString());
      JsonNode before = fixture.call("GET","/api/me/rental-orders/"+order,null,fixture.buyer,null,200);
      assertEquals("receipt_issue_paused",before.path("displayStatus").asString());
      assertEquals(issueId,before.path("disputes").path("issueIds").get(0).asString());
      String requestKey=fixture.key();
      Map<String,Object> reason=view("expectedVersion",0,"reason","请补充破损处近照和说明");
      fixture.call("POST",adminIssue+"/request-evidence",reason,null,requestKey,200);
      fixture.call("POST",adminIssue+"/request-evidence",reason,null,requestKey,200);
      assertEquals("请补充破损处近照和说明",fixture.call("GET",myIssue,null,fixture.buyer,null,200).path("supplementRequest").asString());
      String photo = D4EvidenceHttpTest.upload(fixture, "/api/me/rental-orders/"+order,"receipt_check",fixture.buyer,200);
      String supplementKey=fixture.key();
      Map<String,Object> supplement = view("expectedVersion",1,"summary","右侧铰链断裂，补近照",
          "evidence",view("evidenceIds",List.of(),"uploadReceiptIds",List.of(photo)));
      fixture.call("POST",myIssue+"/supplement",supplement,fixture.buyer,supplementKey,200);
      fixture.call("POST",myIssue+"/supplement",supplement,fixture.buyer,supplementKey,200);
      JsonNode amended=fixture.call("GET",adminIssue,null,null,null,200);
      assertEquals("processing",amended.path("status").asString());
      assertEquals(2,amended.path("evidenceIds").size());
      assertEquals(2,amended.path("version").asInt());
      if (System.getenv("MJ_D5_NODE") != null) {
        Process pages=new ProcessBuilder(System.getenv("MJ_D5_NODE"),"../mujian-frontend/tests/d5-issue-pages-http.cjs")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
          try (OutputStream input=pages.getOutputStream()) {
            input.write(fixture.f.json.writeValueAsBytes(view("base",fixture.f.base,"cookie",fixture.f.adminCookie,
                "order",order,"issue",issueId,"token",fixture.buyer.token(),"out",java.nio.file.Path.of("target/d5").toAbsolutePath().toString())));
          }
          assertTrue(pages.waitFor(60,java.util.concurrent.TimeUnit.SECONDS));
          assertEquals(0,pages.exitValue());
        } finally { if (pages.isAlive()) pages.destroyForcibly(); }
      }
      fixture.f.clock.at = fixture.f.clock.at.plus(Duration.ofDays(1));
      fixture.f.app.getBean(FulfillmentRecovery.class).recoverReceipts();
      body.put("expectedVersion", 1);
      body.put("summary", "另一件道具破损");
      JsonNode second = fixture.call("POST",path,body,fixture.buyer,fixture.key(),200);
      assertNotEquals(first.path("resourceId"),second.path("resourceId"));
      assertEquals(10800000L,db.queryForObject("SELECT remaining_check_ms FROM order_receipt_check WHERE rental_order_id=?",Long.class,order));
      assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM order_issue_report WHERE rental_order_id=?",Integer.class,order));
      FulfillmentRecovery job = fixture.f.app.getBean(FulfillmentRecovery.class);
      LocalDateTime resumedAt = LocalDateTime.ofInstant(fixture.f.clock.at,ZoneId.of("Asia/Shanghai"));
      fixture.f.sessions.revoke(fixture.f.adminCookie.substring("mj_admin_dev=".length()));
      fixture.f.adminCookie = "mj_admin_dev="+fixture.f.sessions.create(fixture.f.admin,"admin").session().getId();
      fixture.f.csrf=fixture.f.call("GET","/api/admin/auth/csrf",null,null,null,200).path("csrfToken").asString();
      Map<String,Object> resume=view("expectedVersion",fixture.call("GET",adminIssue,null,null,null,200).path("version").asInt(),"direction","continue","solutionSummary","双方同意继续履约",
          "agreement",view("summary","已核实双方同意继续","confirmedAt",com.mujian.backend.common.support.D2Values.time(resumedAt),"evidenceIds",List.of()),
          "references",List.of(),"serviceRefund",null);
      fixture.call("POST",adminIssue+"/resolution",resume,null,fixture.key(),200);
      job.recoverReceipts();
      assertNull(db.queryForObject("SELECT deadline_at FROM order_receipt_check WHERE rental_order_id=?",LocalDateTime.class,order));
      resume.put("expectedVersion",0);
      fixture.call("POST","/api/admin/issues/"+second.path("resourceId").asString()+"/resolution",resume,null,fixture.key(),200);
      job.recoverReceipts();
      job.recoverReceipts();
      assertEquals(resumedAt.plusHours(3),db.queryForObject("SELECT deadline_at FROM order_receipt_check WHERE rental_order_id=?",LocalDateTime.class,order));
      assertEquals("pending",db.queryForObject("SELECT check_status FROM order_receipt_check WHERE rental_order_id=?",String.class,order));
      fixture.f.clock.at=fixture.f.clock.at.plus(Duration.ofHours(3));
      job.recoverReceipts();
      assertEquals("auto_confirmed",db.queryForObject("SELECT check_status FROM order_receipt_check WHERE rental_order_id=?",String.class,order));
    } finally {
      if (fixture.f.db!=null && fixture.factoryId!=null) {
        JdbcTemplate db=fixture.f.db;
        for (String table:List.of("admin_operation_audit","admin_case_relation","admin_case_action"))
          db.update("DELETE x FROM "+table+" x JOIN admin_case c ON c.id=x.admin_case_id WHERE c.factory_id=? AND c.case_type='receipt_issue'",fixture.factoryId);
        db.update("DELETE FROM admin_case WHERE factory_id=? AND case_type='receipt_issue'",fixture.factoryId);
        db.update("DELETE e FROM order_evidence_record e JOIN rental_order o ON o.id=e.rental_order_id WHERE o.factory_id=? AND e.issue_report_id IS NOT NULL",fixture.factoryId);
        for (String table:List.of("order_issue_item","order_issue_report"))
          db.update("DELETE x FROM "+table+" x JOIN rental_order o ON o.id=x.rental_order_id WHERE o.factory_id=?",fixture.factoryId);
      }
      setup.cleanup(fixture);
    }
  }
}
