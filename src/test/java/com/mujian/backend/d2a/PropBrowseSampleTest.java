package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.dictionary.DictionaryCache;
import com.mujian.backend.common.enums.DictionaryGroup;
import com.mujian.backend.common.security.D2Contract;
import com.mujian.backend.common.security.D2Contract.Route;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.Fault;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.prop.converter.*;
import com.mujian.backend.prop.dto.PropBrowseQueryDTO;
import com.mujian.backend.prop.policy.*;
import com.mujian.backend.prop.service.*;
import com.mujian.backend.prop.service.PropBrowseService;
import com.mujian.backend.prop.utils.*;
import com.mujian.backend.prop.vo.PropCardVO;
import java.util.*;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropBrowseSampleTest {
  final D2CApplicationTest c = new D2CApplicationTest();
  final D2ApplicationTest f = c.f;
  final String marker = "样板" + Ids.next().substring(0, 8);
  final List<String> ids = new ArrayList<>();
  D2ApplicationTest.User renter, supplier;

  /** 初始化本组隔离依赖。 */
  @BeforeAll
  void start() throws Exception {
    f.start(
        0,
        "--mj.prop.qr-worker-enabled=false",
        "--spring.session.data.redis.namespace=mujian:test:structure-sample:session");
    f.wx.delegate = null;
    renter = f.user();
    supplier = c.supplier();
    f.store.visualSigner = (key, expires) -> "https://example.invalid/" + key;
    for (int i = 0; i < 4; i++) {
      Map<String, Object> fields = c.fields(supplier);
      fields.put("propName", marker + (i == 0 ? "%_" : "普通") + i);
      fields.put("materialDesc", marker + "材质");
      fields.put("styleCodes", List.of("retro", "modern"));
      JsonNode made =
          c.call(
              "POST",
              "/api/me/factory-props",
              view(
                  "assetType",
                  i == 0 ? "A_ASSET" : "B_CONSUMABLE",
                  "fields",
                  fields,
                  "initialQuantity",
                  6,
                  "applyRemark",
                  null),
              supplier,
              Ids.next(),
              200);
      ids.add(made.path("skuId").asString());
      c.approve("sku_audit", made.path("targetId").asString(), 0);
      // 测试库专用排列：把不可租B类放在最新位置，检出先LIMIT后排序错误。
      f.db.update(
          "UPDATE prop_sku SET created_at=DATE_ADD('2026-01-01',INTERVAL ? SECOND) WHERE id=?",
          i,
          ids.get(i));
    }
    // 只为隔离查询测试建立已贴标可租状态；不重跑未改动的二维码外部流程。
    f.db.update("UPDATE prop_sn_instance SET instance_status='idle' WHERE sku_id=?", ids.get(0));
    f.db.update(
        "UPDATE prop_sku_stock SET total_quantity=0,rentable_base_quantity=0,available_quantity=0"
            + " WHERE sku_id=?",
        ids.get(3));
  }

  /** 释放本组创建的资源。 */
  @AfterAll
  void stop() {
    c.stop();
  }

  /** 读取指定测试资源。 */
  JsonNode get(String path, D2ApplicationTest.User actor, int status) throws Exception {
    JsonNode data = c.call("GET", path, null, actor, null, status);
    if (status == 200) {
      D2Contract contract = f.app.getBean(D2Contract.class);
      Route route = contract.route(path.split("\\?")[0], "GET");
      JsonNode schema =
          route
              .operation()
              .path("responses")
              .path("200")
              .path("content")
              .path("application/json")
              .path("schema");
      contract.validate(
          schema,
          f.json.valueToTree(
              view("code", "OK", "message", "成功", "data", data, "traceId", Ids.next())));
    }
    return data;
  }

  /** 验证三个列表入口保持协议、权限及筛选分页。 */
  @Test
  void threeEntrancesKeepWireTypesPermissionsFiltersAndPaging() throws Exception {
    JsonNode all = get("/api/me/props?keyword=" + c.query(marker), renter, 200);
    assertEquals("4", all.path("total").asString());
    assertTrue(all.path("total").isString());
    assertEquals(20, all.path("pageSize").asInt());
    assertEquals(ids.get(3), all.path("items").get(0).path("skuId").asString());
    JsonNode card = all.path("items").get(0);
    assertTrue(card.path("dailyRentPriceFen").isIntegralNumber());
    assertEquals("retro", card.path("styles").get(0).path("code").asString());
    assertEquals("modern", card.path("styles").get(1).path("code").asString());
    assertEquals(
        "1",
        get("/api/me/props?keyword=" + c.query(marker + "%_"), renter, 200)
            .path("total")
            .asString());
    assertEquals(
        "0",
        get("/api/me/props?keyword=" + c.query(marker + "材质"), renter, 200)
            .path("total")
            .asString());
    assertEquals(
        "4",
        get(
                "/api/me/props?keyword=" + c.query(marker) + "&styleCodes=retro&styleCodes=modern",
                renter,
                200)
            .path("total")
            .asString());
    JsonNode empty =
        get(
            "/api/me/props?keyword=" + c.query(marker) + "&page=2147483647&pageSize=100",
            renter,
            200);
    assertEquals("4", empty.path("total").asString());
    assertTrue(empty.path("items").isEmpty());
    get("/api/public/props", null, 200);
    get("/api/public/props?keyword=x", null, 400);
    get("/api/me/props", null, 401);
    get("/api/me/props", supplier, 403);
    for (String value : List.of("0", "-1", "", "01", "2147483648"))
      get("/api/public/props?page=" + value, null, 400);
    get("/api/public/props?pageSize=101", null, 400);
    assertTrue(get("/api/public/props?styleCodes=unknown", null, 200).path("items").isEmpty());
    get("/api/public/props?styleCodes=modern&styleCodes=modern", null, 400);
  }

  /** 名称包含命中，材质与风格名称不命中；普通/租期列表及独立筛选一致。 */
  @Test
  void keywordOnlyMatchesNameAndKeepsIndependentFilters() throws Exception {
    String oldStyleLabel =
        f.db.queryForObject(
            "SELECT option_label FROM option_dictionary WHERE option_group='prop_sku_style-style'"
                + " AND option_code='retro'",
            String.class);
    String styleOnlyTerm = marker + "风格专用";
    DictionaryCache dictionaryCache = f.app.getBean(DictionaryCache.class);
    try {
      f.db.update(
          "UPDATE option_dictionary SET option_label=? WHERE option_group='prop_sku_style-style'"
              + " AND option_code='retro'",
          styleOnlyTerm);
      assertTrue(dictionaryCache.refresh());
      String date = now(f.clock).toLocalDate().toString();
      for (String root :
          List.of(
              "/api/me/props?",
              "/api/me/props/rentable?startDate=" + date + "&endDate=" + date + "&")) {
        // 去掉名称开头两个字后仍能命中，证明保留包含匹配而非前缀。
        String nameTerm = marker.substring(2);
        JsonNode first =
            get(
                root + "keyword=" + c.query(nameTerm) + "&styleCodes=retro&pageSize=2",
                renter,
                200);
        JsonNode second =
            get(
                root + "keyword=" + c.query(nameTerm) + "&styleCodes=retro&pageSize=2&page=2",
                renter,
                200);
        assertEquals("4", first.path("total").asString());
        assertEquals(first.path("total"), second.path("total"));
        assertEquals(2, first.path("items").size());
        assertEquals(2, second.path("items").size());
        Set<String> found = new HashSet<>();
        for (JsonNode page : List.of(first, second)) {
          for (JsonNode item : page.path("items")) {
            JsonNode prop = item.has("prop") ? item.path("prop") : item;
            assertTrue(prop.path("propName").asString().contains(nameTerm));
            found.add(prop.path("skuId").asString());
          }
        }
        assertEquals(new HashSet<>(ids), found);
        for (String term : List.of(marker + "材质", styleOnlyTerm)) {
          JsonNode empty = get(root + "keyword=" + c.query(term), renter, 200);
          assertEquals("0", empty.path("total").asString());
          assertTrue(empty.path("items").isEmpty());
        }
        JsonNode otherStyle =
            get(root + "keyword=" + c.query(nameTerm) + "&styleCodes=special_asset", renter, 200);
        assertEquals("0", otherStyle.path("total").asString());
        assertTrue(otherStyle.path("items").isEmpty());
      }
    } finally {
      f.db.update(
          "UPDATE option_dictionary SET option_label=? WHERE option_group='prop_sku_style-style'"
              + " AND option_code='retro'",
          oldStyleLabel);
      assertTrue(dictionaryCache.refresh());
    }
  }

  /** 检查租期按全量候选排序并保留 A/B 库存。 */
  @Test
  void rentalOrdersAllCandidatesBeforePaginationAndKeepsABQuantities() throws Exception {
    String date = now(f.clock).toLocalDate().toString();
    String root =
        "/api/me/props/rentable?keyword="
            + c.query(marker)
            + "&startDate="
            + date
            + "&endDate="
            + date;
    JsonNode all = get(root + "&pageSize=100", renter, 200);
    assertEquals(
        List.of(ids.get(2), ids.get(1), ids.get(0), ids.get(3)),
        all.path("items").valueStream().map(n -> n.path("prop").path("skuId").asString()).toList());
    for (int i = 0; i < 4; i++) {
      JsonNode page = get(root + "&pageSize=1&page=" + (i + 1), renter, 200);
      assertEquals(all.path("items").get(i), page.path("items").get(0));
      assertEquals(
          i == 3 ? 0 : 6,
          page.path("items").get(0).path("availability").path("maxRentableQuantity").asInt());
    }
    assertEquals(
        "STOCK_NOT_ENOUGH",
        all.path("items").get(3).path("availability").path("reasonCode").asString());
    get("/api/me/props/rentable", renter, 400);
    get(root + "&page=2147483647", renter, 200);
  }

  /** 比较批量组装与原详情卡并检查固定查询次数。 */
  @Test
  void batchAssemblyMatchesExistingDetailCardsAndHasConstantQueryCount() throws Exception {
    PropBrowseService propBrowseService = f.app.getBean(PropBrowseService.class);
    Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    PropBrowseQueryDTO propBrowseQueryDTO = new PropBrowseQueryDTO(1, 100, null, null, null, marker, null, null);
    PageResult<PropCardVO> result = propBrowseService.search(renter.id(), propBrowseQueryDTO);
    PropQueryService propQueryService = f.app.getBean(PropQueryService.class);
    // 业务整理后不再反射已移除的私有card方法，使用真实详情入口核对相同卡片字段。
    fault.sqlCalls.set(0);
    long oldStart = System.nanoTime();
    ArrayList<Object> expected = new ArrayList<Object>();
    for (PropCardVO propCardVO : result.items()) {
      expected.add(
          propQueryService
              .detail(null, propCardVO.getSkuId(), new com.mujian.backend.prop.dto.PropRentalPeriodQueryDTO())
              .getProp());
    }
    double oldMs = (System.nanoTime() - oldStart) / 1_000_000.0;
    int oldCount = fault.sqlCalls.get();
    JsonNode detailCards = f.json.valueToTree(expected);
    for (JsonNode detailCard : detailCards) {
      assertTrue(detailCard.path("factory").has("avatar"));
      ((tools.jackson.databind.node.ObjectNode) detailCard.path("factory")).remove("avatar");
    }
    assertEquals(detailCards, f.json.valueToTree(result.items()));
    List<String> signedKeys = new ArrayList<>();
    f.store.visualSigner =
        (key, expires) -> {
          signedKeys.add(key);
          return "https://example.invalid/" + key;
        };
    fault.sqlCalls.set(0);
    long newStart = System.nanoTime();
    PageResult<PropCardVO> measured = propBrowseService.search(renter.id(), propBrowseQueryDTO);
    double searchMs = (System.nanoTime() - newStart) / 1_000_000.0;
    int searchCount = fault.sqlCalls.get();
    assertEquals(4, searchCount);
    Set<String> coverUrls = new HashSet<>();
    for (PropCardVO propCardVO : measured.items()) {
      assertFalse(f.json.valueToTree(propCardVO).path("factory").has("avatar"));
      if (propCardVO.getCover() != null) coverUrls.add(propCardVO.getCover().getUrl());
    }
    assertEquals(coverUrls.size(), signedKeys.size(), "列表只为去重后的封面签名");
    signedKeys.clear();
    assertNotNull(
        propQueryService
            .detail(null, ids.get(0), new com.mujian.backend.prop.dto.PropRentalPeriodQueryDTO())
            .getProp()
            .getFactory()
            .getAvatar(),
        "真实头像仍随详情返回");
    assertEquals(f.json.valueToTree(result.items()), f.json.valueToTree(measured.items()));
    System.out.printf(
        Locale.ROOT,
        "BROWSE_SAMPLE newFullSearch4=%d SQL/%.2fms (includes"
            + " authorization/count/filter/page/assembly)%n",
        searchCount,
        searchMs);
    // 公开列表不含身份查询；分别请求pageSize=1与pageSize=100（实际返回条数依数据集），数据库语句不随卡片数增长。
    int[] counts = new int[2];
    double[] times = new double[2];
    for (int i = 0; i < 2; i++) {
      fault.sqlCalls.set(0);
      long start = System.nanoTime();
      PageResult<PropCardVO> page =
          propBrowseService.browse(
              new PropBrowseQueryDTO(1, i == 0 ? 1 : 100, null, null, null, null, null, null));
      assertFalse(page.items().isEmpty());
      times[i] = (System.nanoTime() - start) / 1_000_000.0;
      counts[i] = fault.sqlCalls.get();
      assertEquals(3, counts[i]);
    }
    System.out.printf(
        Locale.ROOT,
        "BROWSE_SAMPLE detailRead4=%d SQL/%.2fms/4cards; newFullPublicPageSize1=%d SQL/%.2fms;"
            + " newFullPublicPageSize100=%d SQL/%.2fms%n",
        oldCount,
        oldMs,
        counts[0],
        times[0],
        counts[1],
        times[1]);
    assertTrue(oldCount >= 16);
  }

  /** 停用类型和风格显示已停用，不增加字典查询，写入校验与完整选项不放宽。 */
  @Test
  void dictionaryMissingValuesDisplayDisabledWithoutLookup() throws Exception {
    DictionaryCache dictionaryCache = f.app.getBean(DictionaryCache.class);
    PropCatalogService propCatalogService = f.app.getBean(PropCatalogService.class);
    PropBrowseService propBrowseService = f.app.getBean(PropBrowseService.class);
    Fault fault = f.app.getBean(Fault.class);
    String typeCode =
        f.db.queryForObject("SELECT type_code FROM prop_sku WHERE id=?", String.class, ids.get(0));
    String fireCode =
        f.db.queryForObject(
            "SELECT fire_resistant_code FROM prop_sku WHERE id=?", String.class, ids.get(0));
    String typeLabel =
        f.db.queryForObject(
            "SELECT option_label FROM option_dictionary WHERE option_group='prop_sku-type' AND"
                + " option_code=?",
            String.class,
            typeCode);
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = dictionaryCache.snapshot();
    try {
      f.db.update(
          "UPDATE option_dictionary SET enabled=0 WHERE (option_group='prop_sku-type' AND"
              + " option_code=?) OR (option_group='prop_sku_style-style' AND option_code IN"
              + " ('retro','modern'))",
          typeCode);
      // 即使缓存仍含旧启用项，写入必须查真实状态并拒绝新的停用选择。
      assertThrows(
          ApiFailure.class,
          () ->
              propCatalogService.validateSelection(
                  typeCode, fireCode, List.of("retro", "modern"), List.of(), List.of()));
      assertSame(dictionarySnapshot, dictionaryCache.snapshot());
      assertTrue(dictionaryCache.refresh());
      assertFalse(
          dictionaryCache.snapshot().get(DictionaryGroup.PROP_SKU_TYPE).containsKey(typeCode));
      fault.sqlCalls.set(0);
      PageResult<PropCardVO> result =
          propBrowseService.search(
              renter.id(), new PropBrowseQueryDTO(1, 100, null, null, null, marker, null, null));
      assertEquals(4, fault.sqlCalls.get(), "仅身份/计数/分页/风格，不补查字典");
      assertEquals(4, result.items().size());
      for (PropCardVO propCardVO : result.items()) {
        assertEquals("已停用", propCardVO.getType().getLabel());
        assertEquals(typeCode, propCardVO.getType().getCode());
        assertFalse(propCardVO.getType().getEnabled());
        assertTrue(propCardVO.getType().getRetained());
        assertEquals(
            List.of("retro", "modern"),
            List.of(
                propCardVO.getStyles().get(0).getCode(), propCardVO.getStyles().get(1).getCode()));
        assertFalse(propCardVO.getStyles().get(0).getEnabled());
        for (com.mujian.backend.prop.vo.OptionVO optionVO : propCardVO.getStyles()) {
          assertEquals("已停用", optionVO.getLabel());
          assertTrue(optionVO.getRetained());
        }
      }
      JsonNode rental =
          get(
              "/api/me/props/rentable?keyword="
                  + c.query(marker)
                  + "&startDate="
                  + now(f.clock).toLocalDate().plusDays(2)
                  + "&endDate="
                  + now(f.clock).toLocalDate().plusDays(3),
              renter,
              200);
      assertEquals(4, rental.path("items").size());
      assertEquals(
          "已停用", rental.path("items").get(0).path("prop").path("type").path("label").asString());
      JsonNode detail = get("/api/public/props/" + ids.get(0), null, 200);
      assertEquals("已停用", detail.path("prop").path("type").path("label").asString());
      assertTrue(detail.path("prop").path("type").path("retained").asBoolean());
      JsonNode owned = get("/api/me/factory-props?keyword=" + c.query(marker), supplier, 200);
      assertEquals(4, owned.path("items").size());
      assertEquals("已停用", owned.path("items").get(0).path("fields").path("typeLabel").asString());
      boolean disabledFound = false;
      for (com.mujian.backend.prop.vo.OptionVO optionVO : propCatalogService.options().getTypes()) {
        if (typeCode.equals(optionVO.getCode())) {
          disabledFound = true;
          assertEquals(typeLabel, optionVO.getLabel(), "完整管理选项保留数据库名称");
          assertFalse(optionVO.getEnabled());
          assertTrue(optionVO.getRetained());
        }
      }
      assertTrue(disabledFound);
      propCatalogService.validateSelection(
          typeCode,
          null,
          List.of("retro", "modern"),
          List.of(typeCode),
          List.of("retro", "modern"));
    } finally {
      f.db.update(
          "UPDATE option_dictionary SET enabled=1 WHERE (option_group='prop_sku-type' AND"
              + " option_code=?) OR (option_group='prop_sku_style-style' AND option_code IN"
              + " ('retro','modern'))",
          typeCode);
      assertTrue(dictionaryCache.refresh());
    }
  }

  /** 真实数据库事务提交后刷新，回滚不刷新；同一事务读取同一快照。 */
  @Test
  void dictionaryRefreshFollowsRealCommitAndRollback() {
    DictionaryCache dictionaryCache = f.app.getBean(DictionaryCache.class);
    TransactionTemplate transactionTemplate =
        new TransactionTemplate(f.app.getBean(PlatformTransactionManager.class));
    Fault fault = f.app.getBean(Fault.class);
    String oldLabel =
        f.db.queryForObject(
            "SELECT option_label FROM option_dictionary WHERE option_group='prop_sku_style-style'"
                + " AND option_code='modern'",
            String.class);
    String newLabel = "字典提交测试" + marker;
    try {
      Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = dictionaryCache.snapshot();
      fault.sqlCalls.set(0);
      transactionTemplate.executeWithoutResult(
          transactionStatus -> {
            assertSame(dictionarySnapshot, dictionaryCache.snapshot());
            f.db.update(
                "UPDATE option_dictionary SET option_label=? WHERE"
                    + " option_group='prop_sku_style-style' AND option_code='modern'",
                newLabel);
            dictionaryCache.refreshAfterCommit();
            dictionaryCache.refreshAfterCommit();
            assertEquals(
                oldLabel,
                dictionaryCache.snapshot().get(DictionaryGroup.PROP_SKU_STYLE).get("modern"));
          });
      assertEquals(1, fault.sqlCalls.get(), "提交后仅加载一次字典");
      assertEquals(
          newLabel, dictionaryCache.snapshot().get(DictionaryGroup.PROP_SKU_STYLE).get("modern"));
      fault.sqlCalls.set(0);
      transactionTemplate.executeWithoutResult(
          transactionStatus -> {
            f.db.update(
                "UPDATE option_dictionary SET option_label='不应发布' WHERE"
                    + " option_group='prop_sku_style-style' AND option_code='modern'");
            dictionaryCache.refreshAfterCommit();
            transactionStatus.setRollbackOnly();
          });
      assertEquals(0, fault.sqlCalls.get(), "回滚不加载字典");
      assertEquals(
          newLabel, dictionaryCache.snapshot().get(DictionaryGroup.PROP_SKU_STYLE).get("modern"));
      assertEquals(
          newLabel,
          f.db.queryForObject(
              "SELECT option_label FROM option_dictionary WHERE option_group='prop_sku_style-style'"
                  + " AND option_code='modern'",
              String.class));
    } finally {
      f.db.update(
          "UPDATE option_dictionary SET option_label=? WHERE option_group='prop_sku_style-style'"
              + " AND option_code='modern'",
          oldLabel);
      assertTrue(dictionaryCache.refresh());
    }
  }

  /** 验证公共分页缺省和非法显式值在各入口一致。 */
  @Test
  void sharedPagingConsumersKeepDefaultsAndRejectExplicitInvalidValues() throws Exception {
    for (String path :
        List.of(
            "/api/admin/realname-verifications",
            "/api/admin/student-verifications",
            "/api/admin/factory-onboarding",
            "/api/admin/factory-profile-changes",
            "/api/me/factory-props")) {
      User actor = path.startsWith("/api/admin/") ? null : supplier;
      JsonNode normal = get(path, actor, 200);
      assertEquals(1, normal.path("page").asInt());
      assertEquals(20, normal.path("pageSize").asInt());
      get(path + "?pageSize=0", actor, 400);
      get(path + "?page=2147483648", actor, 400);
      JsonNode empty = get(path + "?page=2147483647&pageSize=100", actor, 200);
      assertTrue(empty.path("items").isEmpty());
    }
  }
  /** 用同一工厂同一筛选采证单条与四条目录查询，核对权限标志和当前资料。 */
  @Test
  void ownedDirectoryUsesBoundedReadsAndPreservesCapabilities() throws Exception {
    PropQueryService propQueryService = f.app.getBean(PropQueryService.class);
    Fault fault = f.app.getBean(Fault.class);
    for (int size : new int[] {1, 4}) {
      com.mujian.backend.prop.dto.OwnedQueryDTO ownedQueryDTO = new com.mujian.backend.prop.dto.OwnedQueryDTO();
      ownedQueryDTO.setPageSize(Integer.toString(size)); ownedQueryDTO.setKeyword(marker);
      fault.sqlCalls.set(0);
      long started = System.nanoTime();
      com.mujian.backend.common.api.PageResult<com.mujian.backend.prop.vo.OwnedSkuVO> result = propQueryService.ownedList(supplier.id(), ownedQueryDTO, false);
      int count = fault.sqlCalls.get();
      System.out.println("OWNED_DIRECTORY size="+size+" sql="+count+" elapsedMicros="+(System.nanoTime()-started)/1000);
      assertTrue(count <= 9, "关联查询不得随页内SKU逐条增长");
      assertEquals(size,result.items().size());
      for (com.mujian.backend.prop.vo.OwnedSkuVO ownedSkuVO : result.items()) {
        assertTrue(ownedSkuVO.getCanEditOptions()); assertNotNull(ownedSkuVO.getFields().getTypeLabel());
        assertEquals(java.util.List.of("retro","modern"),ownedSkuVO.getFields().getStyleCodes());
      }
    }
  }

  /** 只读批量上下文不得把停用账号或停业工厂显示为可操作；修改仅在隔离事务中回滚。 */
  @Test
  void ownedCapabilitiesKeepAccountAndFactoryRestrictions() {
    PropQueryService propQueryService = f.app.getBean(PropQueryService.class);
    org.springframework.transaction.support.TransactionTemplate transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(f.app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    for (String restriction : List.of("account", "factory")) {
      transactionTemplate.executeWithoutResult(transactionStatus -> {
        if (restriction.equals("account")) f.db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", supplier.id());
        else f.db.update("UPDATE factory_profile SET factory_status='suspended' WHERE owner_user_id=?", supplier.id());
        com.mujian.backend.prop.dto.OwnedQueryDTO ownedQueryDTO = new com.mujian.backend.prop.dto.OwnedQueryDTO();
        ownedQueryDTO.setKeyword(marker);
        com.mujian.backend.common.api.PageResult<com.mujian.backend.prop.vo.OwnedSkuVO> result = propQueryService.ownedList(supplier.id(), ownedQueryDTO, false);
        assertEquals(4, result.items().size());
        for (com.mujian.backend.prop.vo.OwnedSkuVO ownedSkuVO : result.items()) {
          assertFalse(ownedSkuVO.getCanEditOptions());
          assertFalse(ownedSkuVO.getCanAdjustInventory());
          assertFalse(propQueryService.owned(supplier.id(), ownedSkuVO.getId(), false).getCanEditOptions());
        }
        transactionStatus.setRollbackOnly();
      });
    }
  }
}
