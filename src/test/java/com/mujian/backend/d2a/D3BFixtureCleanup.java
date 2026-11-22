package com.mujian.backend.d2a;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.ConnectionCallback;

/** 只回收本次显式创建的隔离对象及其外键子行，不使用清库或全库差集。 */
final class D3BFixtureCleanup {
  private D3BFixtureCleanup() {}

  record Link(String child, String column, String parent) {}

  /** 连接级关闭约束仅用于删除已完整收集的循环外键集合，结束必恢复且失败整体回滚。 */
  static void remove(D2ApplicationTest fixture) {
    if (fixture.db == null) return;
    fixture.db.execute(
        (ConnectionCallback<Void>)
            connection -> {
              String catalog = connection.getCatalog();
              if (!catalog.matches("darkmujian_d1_test_[a-zA-Z0-9_]+"))
                throw new IllegalStateException("夹具回收仅允许既有隔离库");
              Map<String, Set<String>> owned = new LinkedHashMap<>();
              owned.put("sys_user", new LinkedHashSet<>(fixture.createdUsers));
              if (fixture.admin != null) owned.get("sys_user").add(fixture.admin);
              for (Map.Entry<String, Set<String>> entry : fixture.cleanupSeeds.entrySet())
                owned.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
              List<Link> links = new ArrayList<>();
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME FROM"
                          + " INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=? AND"
                          + " REFERENCED_TABLE_SCHEMA=? AND REFERENCED_COLUMN_NAME='id'")) {
                statement.setString(1, catalog);
                statement.setString(2, catalog);
                try (ResultSet rows = statement.executeQuery()) {
                  while (rows.next())
                    links.add(new Link(rows.getString(1), rows.getString(2), rows.getString(3)));
                }
              }
              // 配置没有创建人外键；只从本次管理员的原操作回执定位对象。
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT resource_type,resource_id FROM trading_operation_record WHERE"
                          + " actor_user_id=? AND resource_type IN"
                          + " ('transport_vehicle_tier','transport_pricing_rule')")) {
                statement.setString(1, fixture.admin);
                try (ResultSet rows = statement.executeQuery()) {
                  while (rows.next()) {
                    String table = rows.getString(1);
                    if (!owned.containsKey(table)) owned.put(table, new LinkedHashSet<>());
                    owned.get(table).add(rows.getString(2));
                  }
                }
              }
              boolean changed;
              do {
                changed = false;
                for (Link link : links) {
                  Set<String> parents = owned.get(link.parent());
                  if (parents == null || parents.isEmpty()) continue;
                  Set<String> children = owned.get(link.child());
                  if (children == null) {
                    children = new LinkedHashSet<>();
                    owned.put(link.child(), children);
                  }
                  for (String parent : new ArrayList<>(parents))
                    try (PreparedStatement statement =
                        connection.prepareStatement(
                            "SELECT id FROM `"
                                + link.child()
                                + "` WHERE `"
                                + link.column()
                                + "`=?")) {
                      statement.setString(1, parent);
                      try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) changed |= children.add(rows.getString(1));
                      }
                    }
                }
              } while (changed);
              for (String shared :
                  List.of(
                      "admin_role",
                      "admin_menu",
                      "platform_rule_document",
                      "system_dictionary_item",
                      "system_dictionary_type"))
                if (owned.containsKey(shared) && !owned.get(shared).isEmpty())
                  throw new IllegalStateException("拒绝回收共享种子: " + shared);
              boolean autoCommit = connection.getAutoCommit();
              connection.setAutoCommit(false);
              try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                try {
                  for (Map.Entry<String, Set<String>> entry : owned.entrySet()) {
                    if (!entry.getKey().matches("[a-z0-9_]+"))
                      throw new IllegalStateException("非法元数据标识");
                    try (PreparedStatement delete =
                        connection.prepareStatement(
                            "DELETE FROM `" + entry.getKey() + "` WHERE id=?")) {
                      for (String id : entry.getValue()) {
                        delete.setString(1, id);
                        delete.addBatch();
                      }
                      delete.executeBatch();
                    }
                  }
                  // 仍有指向回收集合的行即回滚，不留下孤儿引用。
                  for (Link link : links) {
                    Set<String> parents = owned.get(link.parent());
                    if (parents == null) continue;
                    for (String parent : parents)
                      try (PreparedStatement check =
                          connection.prepareStatement(
                              "SELECT COUNT(*) FROM `"
                                  + link.child()
                                  + "` WHERE `"
                                  + link.column()
                                  + "`=?")) {
                        check.setString(1, parent);
                        try (ResultSet rows = check.executeQuery()) {
                          rows.next();
                          if (rows.getLong(1) != 0) throw new IllegalStateException("隔离夹具仍有外键引用");
                        }
                      }
                  }
                  connection.commit();
                } catch (Exception failure) {
                  connection.rollback();
                  throw failure;
                } finally {
                  statement.execute("SET FOREIGN_KEY_CHECKS=1");
                  connection.setAutoCommit(autoCommit);
                }
              }
              return null;
            });
    for (D2ApplicationTest.User user : fixture.createdSessions)
      fixture.sessions.revoke(user.token());
    if (fixture.adminCookie != null)
      fixture.sessions.revoke(fixture.adminCookie.substring(fixture.adminCookie.indexOf('=') + 1));
  }
}
