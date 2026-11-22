package com.mujian.backend.structure;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.D2Contract;
import com.mujian.backend.common.support.D2Values;
import com.mujian.backend.prop.dto.PropBrowseQueryDTO;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CommonBoundaryTest {
  /** 区分缺省分页与显式非法值。 */
  @Test
  void pagingDefaultsAndExplicitInvalidValues() {
    assertEquals(20, PageQuery.of(null, null).pageSize());
    assertEquals(214748364600L, new PageQuery(Integer.MAX_VALUE, 100).offset());
    for (String value : new String[] {"", "0", "-1", "1.1", " 1", "01", "2147483648"})
      assertThrows(ApiFailure.class, () -> PageQuery.parse(value, 1, Integer.MAX_VALUE));
    assertThrows(
        ApiFailure.class, () -> new PropBrowseQueryDTO(1, 101, null, null, null, null, null, null));
  }

  /** 确认内部展示异常保留服务端分类。 */
  @Test
  void internalViewErrorsDoNotMasqueradeAsUserInput() {
    for (Object[] args : new Object[][] {{"a"}, {4, "x"}, {"a", null, "a", 1}}) {
      IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> D2Values.view(args));
      assertEquals(500, FailureResolver.resolve(failure).status);
    }
    assertTrue(D2Values.view("a", null).containsKey("a"));
  }

  /** 检查数据库和绑定错误的原始原因。 */
  @Test
  void databaseAndBindingClassificationRetainsCause() {
    CannotGetJdbcConnectionException disconnected =
        new CannotGetJdbcConnectionException("secret", new SQLException("password", "08001"));
    assertEquals(503, FailureResolver.resolve(disconnected).status);
    assertSame(disconnected, FailureResolver.resolve(disconnected).getCause());
    assertEquals(
        500,
        FailureResolver.resolve(
                new BadSqlGrammarException("read", "secret sql", new SQLException()))
            .status);
    assertEquals(
        500, FailureResolver.resolve(new DuplicateKeyException("unexpected constraint")).status);
    assertEquals(500, FailureResolver.resolve(new NumberFormatException("internal secret")).status);
    assertEquals(
        400,
        FailureResolver.resolve(
                new org.springframework.validation.BindException(new Object(), "query"))
            .status);
    assertEquals(
        405,
        FailureResolver.resolve(
                new org.springframework.web.HttpRequestMethodNotSupportedException("PUT"))
            .status);
    ApiFailure known = new ApiFailure(409, "STATE_CONFLICT", "VERSION_CONFLICT");
    assertSame(known, FailureResolver.resolve(new RuntimeException(known)));
  }

  /** 检查诊断脱敏且只记一次。 */
  @Test
  void diagnosticsExcludeRawMessagesAndLogOnlyOnce() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private/secret");
    request.setAttribute("diagnosticRoute", "/private/{id}");
    IllegalStateException error = new IllegalStateException("AKID-secret", new SQLException("SQL phone token"));
    String safe = FailureDiagnostics.locations(error);
    assertFalse(safe.contains("AKID-secret"));
    assertFalse(safe.contains("SQL phone token"));
    assertTrue(safe.contains("SQLException"));
    assertTrue(safe.contains("CommonBoundaryTest"));
    Logger logger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(FailureDiagnostics.class);
    ListAppender<ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      FailureDiagnostics.request(request, FailureResolver.resolve(error), error);
      FailureDiagnostics.request(request, FailureResolver.resolve(error), error);
      assertEquals(1, appender.list.size());
      assertFalse(appender.list.get(0).getFormattedMessage().contains("/private/secret"));
    } finally {
      logger.detachAppender(appender);
    }
  }

  /** 覆盖组合契约、引用并列约束及超大数值。 */
  @Test
  void contractRefSiblingsCompositesAndHugeNumbers() throws Exception {
    JsonMapper json = JsonMapper.builder().build();
    D2Contract validator = new D2Contract(json);
    JsonNode sibling = json.readTree("{\"$ref\":\"#/components/schemas/C_Code\",\"maxLength\":2}");
    assertThrows(ApiFailure.class, () -> validator.validate(sibling, json.valueToTree("modern")));
    validator.validate(sibling, json.valueToTree("ab"));
    JsonNode composite =
        json.readTree("{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}],\"minLength\":3}");
    assertThrows(ApiFailure.class, () -> validator.validate(composite, json.valueToTree("a")));
    JsonNode limit = json.readTree("{\"type\":\"integer\",\"maximum\":2147483647}");
    assertThrows(
        ApiFailure.class, () -> validator.validate(limit, json.readTree("18446744073709551617")));
    JsonNode dictionary =
        json.readTree("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\"}}");
    validator.validate(dictionary, json.readTree("{\"a\":1}"));
    assertThrows(
        ApiFailure.class, () -> validator.validate(dictionary, json.readTree("{\"a\":\"x\"}")));
    // pattern是搜索语义，不隐含全字符串锚定。
    validator.validate(
        json.readTree("{\"type\":\"string\",\"pattern\":\"abc\"}"), json.valueToTree("xabcx"));
  }
}
