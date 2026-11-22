package com.mujian.backend.d2a;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only replacement for the former JDBC mapper injection points. */
@Intercepts({
  @Signature(
      type = Executor.class,
      method = "update",
      args = {MappedStatement.class, Object.class}),
  @Signature(
      type = Executor.class,
      method = "query",
      args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
  @Signature(
      type = Executor.class,
      method = "flushStatements",
      args = {}),
  @Signature(
      type = StatementHandler.class,
      method = "prepare",
      args = {Connection.class, Integer.class})
})
public final class MappingFaultInterceptor implements Interceptor {
  private final D2ApplicationTest.Fault fault;
  private final JdbcTemplate jdbc;

  public MappingFaultInterceptor(D2ApplicationTest.Fault fault, JdbcTemplate jdbc) {
    this.fault = fault;
    this.jdbc = jdbc;
  }

  /** 对命中的映射调用进行观测或故障注入。 */
  public Object intercept(Invocation call) throws Throwable {
    String method = call.getMethod().getName();
    if (method.equals("prepare")) {
      String sql =
          ((StatementHandler) call.getTarget())
              .getBoundSql()
              .getSql()
              .replaceAll("\\s+", " ")
              .trim();
      if (fault.observedRequestPath != null
          && org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
              instanceof org.springframework.web.context.request.ServletRequestAttributes attributes
          && fault.observedRequestPath.equals(attributes.getRequest().getRequestURI()))
        fault.preparedSql.add(sql);
      fault.sqlCalls.incrementAndGet();
      if (sql.contains("SELECT EXISTS(SELECT 1 FROM factory_settlement_batch b"))
        fault.settlementVisibilityReads.incrementAndGet();
      if (sql.contains("SUM(w.actual_unlock_amount_fen)"))
        fault.settlementUnlockSums.incrementAndGet();
      if (sql.contains("source_paused OR f.timer_paused") && sql.contains("termination_active FROM"))
        fault.disputeFactReads.incrementAndGet();
      if (sql.contains("FROM admin_menu ORDER BY sort_no,id")) fault.adminPermissionReads.incrementAndGet();
      if (sql.contains("FROM prop_qr_code WHERE target_type=? AND target_id IN"))
        fault.activeQrQueries.incrementAndGet();
      return call.proceed();
    }
    if (method.equals("flushStatements")) {
      Object result = call.proceed();
      if (result instanceof List<?> rows) fault.batchCalls.addAndGet(rows.size());
      return result;
    }
    MappedStatement ms = (MappedStatement) call.getArgs()[0];
    BoundSql bound = ms.getBoundSql(call.getArgs()[1]);
    String sql = bound.getSql().replaceAll("\\s+", " ").trim();
    if (method.equals("update")) fault.check(sql, jdbc);
    Object result = call.proceed();
    if (method.equals("query")
        && fault.inviteReadTarget != null
        && sql.contains(" FROM factory_invite_code WHERE id=?")
        && !sql.contains("FOR UPDATE")) {
      MetaObject meta = ms.getConfiguration().newMetaObject(call.getArgs()[1]);
      for (ParameterMapping parameter : bound.getParameterMappings()) {
        String property = parameter.getProperty();
        Object value =
            bound.hasAdditionalParameter(property)
                ? bound.getAdditionalParameter(property)
                : meta.getValue(property);
        if (fault.inviteReadTarget != null && fault.inviteReadTarget.equals(value)) {
          fault.inviteReadTarget = null;
          fault.inviteReadEntered.countDown();
          if (!fault.inviteReadRelease.await(10, TimeUnit.SECONDS))
            throw new IllegalStateException("invite barrier timeout");
          break;
        }
      }
    }
    return result;
  }
}
