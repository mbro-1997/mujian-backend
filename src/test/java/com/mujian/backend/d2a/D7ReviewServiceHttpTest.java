package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.now;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.d2a.D6CustomPurchaseHttpTest.DeliverySample;
import com.mujian.backend.reward.job.RentalRewardRecoveryJob;
import com.mujian.backend.reward.mapper.RentalRewardMapper;
import com.mujian.backend.reward.service.RentalRewardIssuer;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

class D7ReviewServiceHttpTest {
  final D3BQuoteTest rental = new D3BQuoteTest();
  final D2ApplicationTest f = rental.f;

  /** 已完成订单由所属工厂查看资格，记录 JDBC 实际准备的 SQL。 */
  @Test
  void factoryEligibilityReadsVerifiedAccountOnce() throws Exception {
    rental.start();
    try {
      String orderId = rental.paidOrder();
      f.db.update("UPDATE rental_order SET order_status='completed' WHERE id=?", orderId);
      D2ApplicationTest.Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
      fault.observedRequestPath = "/api/me/review-orders/" + orderId + "/eligibility";
      String endpoint = fault.observedRequestPath + "?businessType=rental_order";
      JsonNode eligible = f.call("GET", endpoint, null, rental.supplier, null, 200);
      fault.observedRequestPath = null;
      assertTrue(eligible.path("canReview").asBoolean());
      assertTrue(eligible.path("reviewId").isNull());
      for (String sql : fault.preparedSql) System.out.println("ELIGIBILITY SQL " + sql);
      long accountReads = fault.preparedSql.stream()
          .filter(sql -> sql.contains("FROM sys_user u LEFT JOIN sys_user_role r")).count();
      assertEquals(1, accountReads);
      assertEquals(1, fault.preparedSql.stream()
          .filter(sql -> sql.equals("SELECT id FROM factory_profile WHERE owner_user_id=?")).count());
      f.call("GET", endpoint, null, rental.fixture.supplier(), null, 404);
      f.db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", rental.supplier.id());
      JsonNode restricted = f.call("GET", endpoint, null, rental.supplier, null, 200);
      assertFalse(restricted.path("canReview").asBoolean());
      assertEquals("ACCOUNT_ACTION_RESTRICTED", restricted.path("reasonCode").asString());
      f.call("POST", "/api/me/order-reviews", Map.of("businessType", "rental_order",
          "businessOrderId", orderId, "rating", 5, "tags", List.of()), rental.supplier, null, 403);
    } finally {
      rental.stop();
    }
  }

  /** 对照案件摘要的实际取列、筛选分页、主来源以及独立详情权限。 */
  @Test
  void caseSummariesAndDetailPermission() throws Exception {
    f.start(0);
    try {
      User owner = f.user();
      String keyword = "scope" + Ids.next();
      List<String> ids = new ArrayList<>();
      for (int index = 0; index < 3; index++) {
        JsonNode created = f.call("POST", "/api/admin/customer-service-cases", Map.of(
            "userId", owner.id(), "demand", keyword + index, "mainConflict", "待核对"),
            null, Ids.next(), 200);
        ids.add(created.path("id").asString());
      }
      f.call("POST", "/api/admin/customer-service-cases/" + ids.get(2) + "/resolve",
          Map.of("version", 0, "resolutionSummary", "已核对", "resultRefs", List.of()), null, Ids.next(), 200);
      String path = "/api/admin/cases";
      String query = path + "?caseType=customer_service&caseStatus=pending&keyword=" + keyword
          + "&pageSize=1&page=";
      List<String> expected = f.db.queryForList(
          "SELECT id FROM admin_case WHERE id IN (?,?) ORDER BY updated_at DESC,id DESC",
          String.class, ids.get(0), ids.get(1));
      D2ApplicationTest.Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
      fault.observedRequestPath = path;
      for (int page = 1; page <= 3; page++) {
        JsonNode response = f.call("GET", query + page, null, null, null, 200);
        assertEquals("2", response.path("total").asString());
        if (page == 3) {
          assertEquals(0, response.path("items").size());
        } else {
          JsonNode row = response.at("/items/0");
          assertEquals(expected.get(page - 1), row.path("id").asString());
          assertEquals("sys_user", row.at("/source/sourceType").asString());
          assertEquals(owner.id(), row.at("/source/sourceId").asString());
          assertFalse(row.has("mainConflict"));
        }
      }
      fault.observedRequestPath = null;
      List<String> listSql = fault.preparedSql.stream()
          .filter(sql -> sql.contains("FROM admin_case c")).distinct().toList();
      assertEquals(2, listSql.size());
      for (String sql : listSql) System.out.println("CASE PAGE SQL " + sql);
      String rows = listSql.stream().filter(sql -> sql.contains("ORDER BY")).findFirst().orElseThrow();
      assertEquals("SELECT c.id,c.case_no,c.case_type,c.case_status,c.title,c.deadline_at, "
          + "r.relation_type AS source_type,r.relation_id AS source_id,c.version", rows.split(" FROM ")[0]);
      assertEquals(1, rows.split(" JOIN ").length - 1);
      assertTrue(rows.contains("JOIN admin_case_relation r ON r.admin_case_id=c.id AND r.is_primary=1"));
      assertTrue(listSql.stream().anyMatch(sql -> sql.startsWith("SELECT COUNT(*) FROM admin_case c WHERE")));
      assertEquals("0", f.call("GET", query.replace("caseType=customer_service", "caseType=other") + "1",
          null, null, null, 200).path("total").asString());
      JsonNode detail = f.call("GET", path + "/" + ids.get(2), null, null, null, 200);
      assertEquals("待核对", detail.path("mainConflict").asString());
      assertEquals("已核对", detail.path("resolutionSummary").asString());
      String menu = "SELECT id FROM admin_menu WHERE permission_code='admin:case:view'";
      List<Map<String, Object>> grants = f.db.queryForList(
          "SELECT * FROM admin_role_menu WHERE admin_menu_id IN (" + menu + ")");
      try {
        assertFalse(grants.isEmpty());
        f.db.update("DELETE FROM admin_role_menu WHERE admin_menu_id IN (" + menu + ")");
        f.call("GET", query + "1", null, null, null, 200);
        f.call("GET", path + "/" + ids.get(2), null, null, null, 403);
      } finally {
        for (Map<String, Object> grant : grants) f.restoreGrant(grant);
      }
    } finally {
      f.stop();
    }
  }

  /** 锁内沿用订单受益人回退，补偿重试返回原券，结案回显该结果引用。 */
  @Test
  void caseWritesKeepOrderBeneficiaryFallback() throws Exception {
    rental.start();
    try {
      String orderId = rental.paidOrder();
      String caseId = f.call("POST", "/api/admin/customer-service-cases",
          Map.of("userId", rental.buyer.id(), "demand", "核对受益人", "mainConflict", "订单来源",
              "source", Map.of("sourceType", "rental_order", "sourceId", orderId)),
          null, Ids.next(), 200).path("id").asString();
      f.db.update("UPDATE admin_case SET buyer_user_id=NULL,rental_order_id=? WHERE id=?", orderId, caseId);
      String templateId = f.db.queryForObject("SELECT template_id FROM user_coupon WHERE id=?",
          String.class, rental.coupon);
      String path = "/api/admin/cases/" + caseId + "/compensation-coupons";
      Map<String, String> request = Map.of("beneficiaryUserId", rental.buyer.id(),
          "templateId", templateId, "reason", "订单核对补偿");
      f.call("POST", path, Map.of("beneficiaryUserId", rental.supplier.id(),
          "templateId", templateId, "reason", "核对归属"), null, Ids.next(), 409);
      String key = Ids.next();
      JsonNode issued = f.call("POST", path, request, null, key, 200);
      assertEquals(issued, f.call("POST", path, request, null, key, 200));
      String couponId = issued.path("id").asString();
      JsonNode closed = f.call("POST", "/api/admin/customer-service-cases/" + caseId + "/resolve",
          Map.of("version", 0, "resolutionSummary", "已补偿", "resultRefs",
              List.of(Map.of("sourceType", "user_coupon", "sourceId", couponId))), null, Ids.next(), 200);
      assertEquals(couponId, closed.at("/resultRefs/0/sourceId").asString());
      assertEquals("closed", closed.path("caseStatus").asString());
    } finally {
      rental.stop();
    }
  }

  /** 核对定制评价双角色访问。 */
  @Test
  void customReviewBothRoles() throws Exception {
    Assumptions.assumeTrue(System.getenv("MJ_TEST_CONFIG") != null);
    assertTrue(
        java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("MJ_TEST_CONFIG")))
            .contains(":33317/darkmujian_d1_test_d4"));
    D6CustomPurchaseHttpTest custom = new D6CustomPurchaseHttpTest();
    custom.start();
    try {
      DeliverySample delivery = custom.delivered(true, false, 1);
      String order = delivery.order(), path = delivery.buyerPath();
      custom.post(
          path + "/accept-delivery",
          Map.of("expectedVersion", custom.version(path, false), "deliveryId", delivery.delivery()),
          false,
          200);
      for (User user : List.of(custom.f.buyer, custom.f.supplier)) {
        JsonNode review =
            custom.f.f.call(
                "POST",
                "/api/me/order-reviews",
                Map.of(
                    "businessType",
                    "custom_order",
                    "businessOrderId",
                    order,
                    "rating",
                    4,
                    "content",
                    "合成交付验收",
                    "tags",
                    List.of()),
                user,
                null,
                200);
        assertTrue(review.get("rewardStatus").isNull());
        String reviewId = review.path("id").asString();
        String eligibility = "/api/me/review-orders/" + order + "/eligibility?businessType=custom_order";
        String detail = "/api/me/order-reviews/" + reviewId;
        assertEquals(reviewId, custom.f.f.call("GET", eligibility, null, user, null, 200).path("reviewId").asString());
        assertEquals("合成交付验收", custom.f.f.call("GET", detail, null, user, null, 200).path("content").asString());
        assertEquals(1, custom.f.f.db.update("UPDATE order_review SET review_status='hidden' WHERE id=?", reviewId));
        JsonNode refreshed = custom.f.f.call("GET", eligibility, null, user, null, 200);
        assertTrue(refreshed.get("reviewId").isNull());
        assertFalse(refreshed.path("canReview").asBoolean());
        custom.f.f.call("GET", detail, null, user, null, 404);
      }
      assertEquals(
          2,
          custom.f.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_review WHERE business_type='custom_order' AND"
                  + " business_order_id=?",
              Integer.class,
              order));
    } finally {
      custom.stop();
    }
  }

  /** 验证奖励恢复的归属与来源上下文。 */
  @Test
  void rewardRecoveryOwnershipAndContext() throws Exception {
    Assumptions.assumeTrue(System.getenv("MJ_TEST_CONFIG") != null);
    assertTrue(
        java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("MJ_TEST_CONFIG")))
            .contains(":33317/darkmujian_d1_test_d4"));
    rental.extraArguments =
        new String[] {"--mj.reward.recovery-enabled=true", "--mj.reward.recovery-delay-ms=600000"};
    rental.start();
    String tpl =
        f.db.queryForObject(
            "SELECT template_id FROM user_coupon WHERE id=?", String.class, rental.coupon);
    ArrayList<String> rules = new ArrayList<String>();
    try {
      for (String e : List.of("first_realname_verified", "first_rental_review")) {
        String r = Ids.next();
        rules.add(r);
        f.db.update(
            "INSERT INTO"
                + " coupon_reward_rule(id,event_type,rule_version,template_id,status,created_by_admin_user_id,created_at,activated_at)"
                + " SELECT ?,?,COALESCE(MAX(rule_version),0)+1,?,'active',?,?,? FROM"
                + " coupon_reward_rule WHERE event_type=?",
            r,
            e,
            tpl,
            f.admin,
            now(f.clock),
            now(f.clock),
            e);
      }
      User other = f.user();
      String rid =
          f.call(
                  "POST",
                  "/api/me/realname-verifications",
                  f.body(other, "realname"),
                  other,
                  Ids.next(),
                  200)
              .at("/application/id")
              .asString();
      String verify = "/api/admin/realname-verifications/" + rid;
      JsonNode submitted = f.call("GET", verify, null, null, null, 200);
      f.call(
          "POST",
          verify + "/approve",
          f.reviewBody(submitted, true, "realname"),
          null,
          Ids.next(),
          200);
      JsonNode batch =
          f.call(
              "POST",
              "/api/admin/coupon-distributions/issue",
              Map.of(
                  "templateId",
                  tpl,
                  "mode",
                  "selected_users",
                  "userIds",
                  List.of(rental.buyer.id()),
                  "quantityPerUser",
                  1,
                  "reason",
                  "冻结分摊对账"),
              null,
              Ids.next(),
              200);
      rental.coupon =
          f.db.queryForObject(
              "SELECT id FROM user_coupon WHERE issue_source_id=? AND user_id=?",
              String.class,
              batch.path("distributionId").asString(),
              rental.buyer.id());
      String order = rental.paidOrder();
      String usage =
          f.db.queryForObject(
              "SELECT id FROM coupon_usage WHERE user_coupon_id=?", String.class, rental.coupon);
      JsonNode held =
          f.call("GET", "/api/admin/user-coupons/" + rental.coupon, null, null, null, 200);
      assertEquals(usage, held.at("/usageIds/0").asString());
      JsonNode allocations =
          f.call(
              "GET", "/api/admin/coupon-usages/" + usage + "/allocations", null, null, null, 200);
      assertEquals(rental.coupon, allocations.path("userCouponId").asString());
      assertTrue(allocations.path("allocations").size() > 0);
      f.call(
          "POST",
          "/api/admin/user-coupons/" + rental.coupon + "/invalidate",
          Map.of("reason", "拒绝作废已用券", "expectedStatus", "available"),
          null,
          Ids.next(),
          409);
      assertEquals("COUPON_NOT_AVAILABLE", f.lastReason);
      String eligibility =
          "/api/me/review-orders/" + order + "/eligibility?businessType=rental_order";
      Map<String, Object> body =
          Map.of(
              "businessType",
              "rental_order",
              "businessOrderId",
              order,
              "rating",
              5,
              "tags",
              List.of(),
              "content",
              "交付记录清晰");
      assertFalse(
          f.call("GET", eligibility, null, rental.buyer, null, 200).path("canReview").asBoolean());
      f.call("POST", "/api/me/order-reviews", body, rental.buyer, null, 409);
      f.call("POST", "/api/me/order-reviews", body, other, null, 404);
      f.db.update("UPDATE rental_order SET order_status='completed' WHERE id=?", order);
      assertTrue(
          f.call("GET", eligibility, null, rental.buyer, null, 200).path("canReview").asBoolean());
      JsonNode review = f.call("POST", "/api/me/order-reviews", body, rental.buyer, null, 200);
      assertEquals(review, f.call("POST", "/api/me/order-reviews", body, rental.buyer, null, 200));
      JsonNode factoryReview =
          f.call("POST", "/api/me/order-reviews", body, rental.supplier, null, 200);
      assertTrue(factoryReview.path("rewardStatus").isNull());
      f.call("GET", "/api/me/order-reviews?direction=received", null, rental.supplier, null, 200);
      RentalRewardIssuer issuer = f.app.getBean(RentalRewardIssuer.class);
      RentalRewardMapper rentalRewardMapper = f.app.getBean(RentalRewardMapper.class);
      for (String source : List.of(rid, review.path("id").asString())) {
        String reward =
            f.db.queryForObject(
                "SELECT id FROM coupon_reward_record WHERE source_id=?", String.class, source);
        org.mockito.Mockito.doThrow(new IllegalStateException("D7 isolated issuance fault"))
            .when(rentalRewardMapper)
            .increaseTemplateIssuedCount(
                org.mockito.ArgumentMatchers.eq(tpl), org.mockito.ArgumentMatchers.any());
        try {
          assertThrows(IllegalStateException.class, () -> issuer.issue(reward));
        } finally {
          org.mockito.Mockito.reset(rentalRewardMapper);
        }
        assertEquals(
            "pending",
            f.db.queryForObject(
                "SELECT reward_status FROM coupon_reward_record WHERE id=?", String.class, reward));
        assertTrue(f.call("GET", verify, null, null, null, 200).toString().contains("verified"));
        assertEquals(
            "pending",
            source.equals(rid)
                ? "pending"
                : f.call("GET", "/api/me/order-reviews/" + source, null, rental.buyer, null, 200)
                    .get("rewardStatus")
                    .asString());
        issuer.defer(reward);
      }
      f.clock.at = f.clock.at.plusSeconds(301);
      RentalRewardRecoveryJob recovery =
          f.app.getBean(com.mujian.backend.reward.job.RentalRewardRecoveryJob.class);
      recovery.recover();
      recovery.recover();
      for (String source : List.of(rid, review.get("id").asString()))
        assertEquals(
            1,
            f.db.queryForObject(
                "SELECT COUNT(*) FROM coupon_reward_record r JOIN user_coupon c ON"
                    + " c.issue_request_id=CONCAT('reward:',r.id) WHERE r.source_id=? AND"
                    + " r.reward_status='granted'",
                Integer.class,
                source));
      String contexts = "/api/me/service-contexts";
      Map<String, String> source = Map.of("sourceType", "rental_order", "sourceId", order);
      Map<String, Map<String, String>> input = Map.of("source", source);
      String key = Ids.next();
      JsonNode card = f.call("POST", contexts, input, rental.buyer, key, 200);
      assertEquals(card, f.call("POST", contexts, input, rental.buyer, key, 200));
      String cid = card.at("/context/id").asString();
      f.call("GET", contexts + "/" + cid, null, other, null, 404);
      f.call("POST", contexts, input, other, Ids.next(), 404);
      f.call("POST", contexts, input, rental.supplier, Ids.next(), 200);
      f.call("GET", "/api/me/service-sources", null, rental.supplier, null, 200);
      User otherFactory = rental.fixture.supplier();
      f.call("POST", contexts, input, otherFactory, Ids.next(), 404);
      String expKey = Ids.next();
      JsonNode exp = f.call("POST", contexts, input, rental.buyer, expKey, 200);
      f.clock.at = f.clock.at.plusSeconds(600);
      assertEquals(
          "expired",
          f.call(
                  "GET",
                  contexts + "/" + exp.at("/context/id").asString(),
                  null,
                  rental.buyer,
                  null,
                  200)
              .get("contextStatus")
              .asString());
      f.call("POST", contexts, input, rental.buyer, expKey, 409);
      assertEquals("CONTEXT_EXPIRED", f.lastReason);
      f.clock.at = f.clock.at.minusSeconds(600);
      String c =
          f.call(
                  "POST",
                  "/api/admin/customer-service-cases",
                  Map.of(
                      "userId",
                      rental.buyer.id(),
                      "contextId",
                      cid,
                      "source",
                      source,
                      "demand",
                      "核对原订单",
                      "mainConflict",
                      "到货沟通"),
                  null,
                  Ids.next(),
                  200)
              .path("id")
              .asString();
      f.call("POST", contexts, input, rental.buyer, key, 409);
      String path = "/api/admin/cases/" + c + "/compensation-coupons";
      f.call(
          "POST",
          path,
          Map.of("beneficiaryUserId", other.id(), "templateId", tpl, "reason", "沟通补偿"),
          null,
          Ids.next(),
          409);
      Map<String, String> comp =
          Map.of("beneficiaryUserId", rental.buyer.id(), "templateId", tpl, "reason", "沟通补偿");
      String k = Ids.next();
      JsonNode coupon = f.call("POST", path, comp, null, k, 200);
      assertEquals(coupon, f.call("POST", path, comp, null, k, 200));
      String couponId = coupon.path("id").asString();
      f.call(
          "POST",
          "/api/admin/customer-service-cases/" + c + "/resolve",
          Map.of(
              "version",
              0,
              "resolutionSummary",
              "补偿券已发放",
              "resultRefs",
              List.of(Map.of("sourceType", "user_coupon", "sourceId", couponId))),
          null,
          Ids.next(),
          200);
      f.call(
          "POST",
          "/api/admin/user-coupons/" + couponId + "/invalidate",
          Map.of("reason", "核对作废", "expectedStatus", "available"),
          null,
          Ids.next(),
          200);
      assertEquals(
          0,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM customer_service_event_log e JOIN customer_service_context c ON"
                  + " e.session_id=c.session_id WHERE c.id=?",
              Integer.class,
              cid));
    } finally {
      for (String id : rules)
        f.db.update(
            "UPDATE coupon_reward_rule SET status='ended',ended_at=? WHERE id=?", now(f.clock), id);
      rental.stop();
    }
  }
}
