package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.infrastructure.persistence.*;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.realname.mapper.RealnameMapper;
import com.mujian.backend.student.mapper.StudentMapper;
import com.mujian.backend.prop.domain.*;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSkuStockDO;
import com.mujian.backend.prop.mapper.*;
import com.mujian.backend.prop.utils.PropBatchWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlXmlMappingTest {
  final D2CApplicationTest c = new D2CApplicationTest();
  final D2ApplicationTest f = c.f;
  FactoryReviewMapper factories;
  String sku, stock, factory;

  /** 初始化本组隔离依赖。 */
  @BeforeAll
  void start() throws Exception {
    c.start();
    factories = f.app.getBean(FactoryReviewMapper.class);
    User supplier = c.supplier();
    JsonNode made = c.create(supplier, "B_CONSUMABLE", 3);
    sku = made.path("skuId").asString();
    String audit = made.path("targetId").asString();
    assertFalse(sku.isBlank());
    assertFalse(audit.isBlank());
    c.approve("sku_audit", audit, 0);
    PropSkuStockDO propSkuStockDO = f.app.getBean(PropSkuStockMapper.class).selectPropSkuStockListBySkuId(sku, false).get(0);
    stock = propSkuStockDO.getId();
    factory = f.app.getBean(PropSkuMapper.class).selectPropSku(sku, false).getFactoryId();
  }

  /** 释放本组创建的资源。 */
  @AfterAll
  void stop() {
    c.stop();
  }

  /** 核对每条 XML 语句的唯一 Java 方法及参数绑定。 */
  @Test
  void everyMapperMethodHasExactlyOneXmlStatementAndExplicitArguments() throws Exception {
    Configuration config = f.app.getBean(SqlSessionFactory.class).getConfiguration();
    HashSet<String> xmlIds = new HashSet<String>();
    for (Resource resource :
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")) {
      String xml = resource.getContentAsString(StandardCharsets.UTF_8);
      Matcher namespace = Pattern.compile("<mapper\\s+namespace=\"([^\"]+)\"").matcher(xml);
      assertTrue(namespace.find());
      Matcher ids = Pattern.compile("<(?:select|insert|update|delete)\\s+id=\"([^\"]+)\"").matcher(xml);
      while (ids.find())
        assertTrue(xmlIds.add(namespace.group(1) + "." + ids.group(1)), "duplicate statement");
      // 先按生产配置展开静态 include，再检查真正交给语言驱动的语句没有字符串替换。
      org.apache.ibatis.builder.MapperBuilderAssistant assistant =
          new org.apache.ibatis.builder.MapperBuilderAssistant(config, resource.getDescription());
      assistant.setCurrentNamespace(namespace.group(1));
      org.apache.ibatis.builder.xml.XMLIncludeTransformer includes =
          new org.apache.ibatis.builder.xml.XMLIncludeTransformer(config, assistant);
      org.apache.ibatis.parsing.XPathParser parser = new org.apache.ibatis.parsing.XPathParser(
          xml, true, config.getVariables(), new org.apache.ibatis.builder.xml.XMLMapperEntityResolver());
      for (org.apache.ibatis.parsing.XNode statement : parser.evalNodes("/mapper/select|/mapper/update|/mapper/insert|/mapper/delete")) {
        org.w3c.dom.Node node = statement.getNode();
        includes.applyIncludes(node);
        assertFalse(node.getTextContent().contains("${"), resource.getFilename() + "." + statement.getStringAttribute("id"));
      }
    }
    HashSet<String> methods = new HashSet<String>();
    for (Class<?> mapper : config.getMapperRegistry().getMappers()) {
      assertTrue(mapper.isInterface());
      for (Method method : mapper.getDeclaredMethods()) {
        String id = mapper.getName() + "." + method.getName();
        // 缺表兼容分支是 Java 组合方法；它委托的实际查询分别有 XML 映射。
        if (method.isDefault()) {
          assertFalse(config.hasStatement(id), id);
          continue;
        }
        assertTrue(methods.add(id));
        assertTrue(config.hasStatement(id), id);
        assertTrue(config.getMappedStatement(id).getResource().contains(".xml"), id);
        for (Parameter parameter : method.getParameters()) {
          // ResultHandler 是 MyBatis 流式消费回调，不参与 SQL 参数绑定。
          if (org.apache.ibatis.session.ResultHandler.class.isAssignableFrom(parameter.getType())) continue;
          assertNotNull(parameter.getAnnotation(Param.class), id);
          if (mapper.getPackageName().equals("com.mujian.backend.prop.mapper")) {
            assertFalse(Map.class.isAssignableFrom(parameter.getType()), id);
          }
        }
        if (mapper.getPackageName().equals("com.mujian.backend.prop.mapper")) {
          assertFalse(method.getGenericReturnType().getTypeName().contains("VerificationRow"), id);
          assertFalse(method.getGenericReturnType().getTypeName().contains("java.util.Map"), id);
        }
      }
    }
    assertEquals(methods, xmlIds);
  }

  /** 对照检查两种查询投影。 */
  private void sameProjection(
      Object actual, Map<String, Object> expected) {
    assertNotNull(actual);
    BeanWrapperImpl bean = new org.springframework.beans.BeanWrapperImpl(actual);
    for (Entry<String, Object> entry : expected.entrySet()) {
      String property = com.mujian.backend.prop.utils.PropJson.camel(entry.getKey());
      assertTrue(bean.isReadableProperty(property), property);
      Object value = entry.getValue();
      if (value instanceof java.sql.Timestamp t) value = t.toLocalDateTime();
      if (value instanceof byte[] bytes) assertArrayEquals(bytes, (byte[]) bean.getPropertyValue(property), property);
      else assertEquals(value, bean.getPropertyValue(property), property);
    }
  }

  // 核对工厂明确投影的每一列，BIGINT版本对外继续保留字符串语义。
  /** 对照检查两种查询投影。 */
  private void sameProjection(com.mujian.backend.factory.projection.FactorySourceProjection actual, Map<String, Object> expected) {
    assertNotNull(actual);
    BeanWrapperImpl bean = new BeanWrapperImpl(actual);
    for (Entry<String, Object> entry : expected.entrySet()) {
      String property = com.mujian.backend.prop.utils.PropJson.camel(entry.getKey());
      assertTrue(bean.isReadableProperty(property), property);
      Object value = entry.getValue();
      if (value instanceof java.sql.Timestamp timestamp) value = timestamp.toLocalDateTime();
      if (value != null && property.equals("profileVersion")) value = value.toString();
      if (value instanceof byte[] bytes) assertArrayEquals(bytes, (byte[]) bean.getPropertyValue(property), property);
      else assertEquals(value, bean.getPropertyValue(property), property);
    }
  }

  /** 对照 JDBC 检查映射类型、空值与缺失记录。 */
  @Test
  void jdbcProjectionTypesNullsGeneratedColumnsAndMissingRowsRemainEquivalent() throws Exception {
    sameProjection(
        f.app.getBean(PropSkuMapper.class).selectPropSku(sku, false),
        f.db.queryForMap("SELECT * FROM prop_sku WHERE id=?", sku));
    sameProjection(
        f.app.getBean(PropSkuStockMapper.class).selectPropSkuStock(stock, false),
        f.db.queryForMap("SELECT * FROM prop_sku_stock WHERE id=?", stock));
    sameProjection(
        factories.selectFactorySourceById(FactorySource.PROFILE, factory, false),
        f.db.queryForMap("SELECT * FROM factory_profile WHERE id=?", factory));
    assertNull(f.app.getBean(PropSkuMapper.class).selectPropSku(Ids.next(), false));
    assertEquals(List.of(), f.app.getBean(PropSnInstanceMapper.class).selectPropSnInstanceListByIds(List.of(Ids.next())));
    User user = f.user();
    for (String domain : List.of("realname", "student")) {
      JsonNode made =
          f.call(
              "POST",
              "/api/me/" + domain + "-verifications",
              f.body(user, domain),
              user,
              Ids.next(),
              200);
      String id = made.path("id").asString();
      if (id.isBlank())
        id =
            f.db.queryForObject(
                "SELECT id FROM user_" + domain + "_verification WHERE user_id=?",
                String.class,
                user.id());
      com.mujian.backend.verification.domain.VerificationApplicationDO verificationApplicationDO =
          domain.equals("realname")
              ? f.app.getBean(RealnameMapper.class).selectRealnameVerificationById(id)
              : f.app.getBean(StudentMapper.class).selectStudentVerificationById(id);
      // Only selected columns are public to the internal JDBC projection (DDL guards are omitted).
      Map<String, Object> all = f.db.queryForMap("SELECT * FROM user_" + domain + "_verification WHERE id=?", id);
      all.keySet().removeIf(column -> !new BeanWrapperImpl(verificationApplicationDO).isReadableProperty(com.mujian.backend.prop.utils.PropJson.camel(column)));
      sameProjection(verificationApplicationDO, all);
    }
  }

  /** 检查固定标识、影响行数、批量预检和回滚。 */
  @Test
  void fixedIdentifiersUpdateCountsBatchPreflightAndRollback() {
    sameProjection(
        f.app.getBean(PropSkuMapper.class).selectPropSku(sku, false),
        f.db.queryForMap("SELECT * FROM prop_sku WHERE id=?", sku));
    sameProjection(
        f.app.getBean(PropSkuStockMapper.class).selectPropSkuStock(stock, false),
        f.db.queryForMap("SELECT * FROM prop_sku_stock WHERE id=?", stock));
    assertNull(f.app.getBean(PropSkuMapper.class).selectPropSku(Ids.next(), false));
    assertFalse(
        java.util.stream.Stream.of(PropSkuMapper.class, PropSkuStockMapper.class, PropQrCodeMapper.class).flatMap(mapperType -> Arrays.stream(mapperType.getMethods()))
            .anyMatch(
                m ->
                    Set.of("find", "insert", "update", "pageRows", "pageCount")
                        .contains(m.getName())));
    TransactionTemplate tx = new TransactionTemplate(f.app.getBean(PlatformTransactionManager.class));
    tx.executeWithoutResult(
        status -> {
          PropSkuDO value = f.app.getBean(PropSkuMapper.class).selectPropSku(sku, true);
          value.setRemark(null);
          f.app.getBean(PropSkuMapper.class).updatePropSkuFields(value);
          assertNull(f.app.getBean(PropSkuMapper.class).selectPropSku(sku, false).getRemark());
          status.setRollbackOnly();
        });
    PropBatchWriter batch = f.app.getBean(PropBatchWriter.class);
    String id = Ids.next();
    PropQrCodeDO source =
        f.app.getBean(PropQrCodeMapper.class).selectPropQrCode(
            f.db.queryForObject(
                "SELECT id FROM prop_qr_code WHERE sku_id=? LIMIT 1", String.class, sku),
            false);
    // Two equal new IDs fail in JDBC batch; the outer transaction must roll back the first insert.
    source.id(id).qrCodeId(Ids.next()).status("revoked");
    assertThrows(
        RuntimeException.class,
        () -> tx.executeWithoutResult(st -> batch.insertQrCodes(List.of(source, source))));
    assertEquals(
        0, f.db.queryForObject("SELECT COUNT(*) FROM prop_qr_code WHERE id=?", Integer.class, id));
    batch.insertQrCodes(List.of());
  }

  /** 验证可选库存锁阻塞并发写入。 */
  @Test
  void optionalStockLockUsesCorrectBindingAndBlocksCompetingWrite() throws Exception {
    Configuration config = f.app.getBean(SqlSessionFactory.class).getConfiguration();
    String id = PropSkuStockMapper.class.getName() + ".selectPropSkuStockListBySkuId";
    for (boolean lock : List.of(false, true)) {
      BoundSql bound = config.getMappedStatement(id).getBoundSql(Map.of("skuId", sku, "lock", lock));
      assertEquals(
          List.of("skuId"),
          bound.getParameterMappings().stream().map(p -> p.getProperty()).toList());
      assertEquals(lock, bound.getSql().contains("FOR UPDATE"));
    }
    TransactionTemplate tx = new TransactionTemplate(f.app.getBean(PlatformTransactionManager.class));
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      tx.executeWithoutResult(
          status -> {
            assertEquals(stock, f.app.getBean(PropSkuStockMapper.class).selectPropSkuStockListBySkuId(sku, true).get(0).getId());
            Future<Integer> result =
                pool.submit(
                    () -> {
                      try (Connection conn = f.app.getBean(javax.sql.DataSource.class).getConnection()) {
                        try (Statement stmt = conn.createStatement()) {
                          stmt.execute("SET SESSION innodb_lock_wait_timeout=1");
                        }
                        try (PreparedStatement stmt =
                            conn.prepareStatement(
                                "UPDATE prop_sku_stock SET updated_at=updated_at WHERE id=?")) {
                          stmt.setString(1, stock);
                          stmt.executeUpdate();
                          return 0;
                        } catch (java.sql.SQLException e) {
                          return e.getErrorCode();
                        } finally {
                          try (Statement stmt = conn.createStatement()) {
                            stmt.execute("SET SESSION innodb_lock_wait_timeout=50");
                          }
                        }
                      }
                    });
            try {
              assertEquals(1205, result.get(5, TimeUnit.SECONDS));
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      assertEquals(
          1,
          f.app.getBean(PropSkuStockMapper.class).updatePropSkuStockByVersion(
              f.app.getBean(PropSkuStockMapper.class).selectPropSkuStock(stock, false).updatedAt(now(f.clock))));
    } finally {
      pool.shutdownNow();
    }
  }
}
