package com.mujian.backend.application.service.admin;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.infrastructure.persistence.mapper.admin.AdminMapper;
import com.mujian.backend.infrastructure.persistence.record.admin.*;
import com.mujian.backend.infrastructure.persistence.record.admin.AdminRecord;
import com.mujian.backend.infrastructure.persistence.record.admin.MenuRecord;
import com.mujian.backend.infrastructure.persistence.record.admin.RoleRecord;
import com.mujian.backend.web.response.admin.*;
import com.mujian.backend.web.response.admin.AdminMeResponse;
import com.mujian.backend.web.response.admin.MenuResponse;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessService {
  public record Access(
      AdminMeResponse me, List<MenuResponse> navigation, List<MenuResponse> configuration) {}

  private final AdminMapper adminMapper;
  private final com.mujian.backend.common.config.MenuRegistry menuRegistry;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public AdminAccessService(
      AdminMapper adminMapper, com.mujian.backend.common.config.MenuRegistry menuRegistry) {
    this.adminMapper = adminMapper;
    this.menuRegistry = menuRegistry;
  }

  /** 检查管理员、角色和菜单完整性并生成权限上下文。 */
  public Access load(String userId) {
    AdminRecord adminRecord = adminMapper.selectAdminByUserId(userId);
    if (adminRecord == null
        || SystemIdentity.SYSTEM_USER_ID.equals(userId)
        || !adminRecord.enabled()
        || !CODE_ACTIVE.equals(adminRecord.userStatus())
        || !D2Codes.CODE_ADMIN.equals(adminRecord.roleCode()))
      throw ApiFailure.denied(CODE_ACCOUNT_ACTION_RESTRICTED);
    List<RoleRecord> roles = adminMapper.selectAdminRolesByAccountId(adminRecord.id());
    if (roles.isEmpty()) throw ApiFailure.denied(CODE_PERMISSION_DENIED);
    for (RoleRecord roleRecord : roles) {
      if (!"platform_admin".equals(roleRecord.roleCode())) throw ApiFailure.inconsistent();
    }
    List<MenuRecord> nodes = adminMapper.selectAllAdminMenus();
    HashSet<String> assigned =
        new HashSet<>(adminMapper.selectAssignedMenuIdsByAccountId(adminRecord.id()));
    HashMap<String, MenuRecord> byId = new HashMap<String, MenuRecord>();
    for (MenuRecord menuRecord : nodes) byId.put(menuRecord.id(), menuRecord);
    for (MenuRecord menuRecord : nodes) {
      HashSet<String> seen = new HashSet<String>();
      MenuRecord menuRecordCurrent = menuRecord;
      while (menuRecordCurrent != null) {
        if (!seen.add(menuRecordCurrent.id())) throw ApiFailure.inconsistent();
        if (menuRecordCurrent.parentId() == null) break;
        MenuRecord menuRecordParent = byId.get(menuRecordCurrent.parentId());
        if (menuRecordParent == null || "button".equals(menuRecordParent.menuType()))
          throw ApiFailure.inconsistent();
        menuRecordCurrent = menuRecordParent;
      }
      if ("menu".equals(menuRecord.menuType())
          && !menuRegistry.contains(menuRecord.routePath(), menuRecord.componentPath()))
        throw ApiFailure.inconsistent();
    }
    HashSet<String> allowed = new HashSet<String>();
    TreeSet<String> permissions = new TreeSet<String>();
    for (MenuRecord menuRecordGranted : nodes) {
      if (!assigned.contains(menuRecordGranted.id())) continue;
      boolean enabled = true;
      for (MenuRecord menuRecordAncestor = menuRecordGranted;
          menuRecordAncestor != null;
          menuRecordAncestor = byId.get(menuRecordAncestor.parentId()))
        enabled &= menuRecordAncestor.enabled();
      if (enabled) {
        allowed.add(menuRecordGranted.id());
        if (menuRecordGranted.permissionCode() != null)
          permissions.add(menuRecordGranted.permissionCode());
      }
    }
    List<AdminMeResponse.Role> roleResponses = new ArrayList<>();
    for (RoleRecord roleRecord : roles) {
      roleResponses.add(new AdminMeResponse.Role(roleRecord.roleCode(), roleRecord.roleName()));
    }
    AdminMeResponse adminMeResponse =
        new AdminMeResponse(
            adminRecord.userId(),
            adminRecord.id(),
            adminRecord.username(),
            adminRecord.nickname(),
            List.copyOf(roleResponses),
            List.copyOf(permissions));
    return new Access(
        adminMeResponse, tree(null, nodes, allowed, true), tree(null, nodes, allowed, false));
  }

  /** 按既有授权与可见性递归组装菜单树。 */
  private List<MenuResponse> tree(
      String parent, List<MenuRecord> nodes, Set<String> allowed, boolean navigation) {
    ArrayList<MenuResponse> result = new ArrayList<MenuResponse>();
    for (MenuRecord menuRecord : nodes) {
      if (!Objects.equals(parent, menuRecord.parentId())) continue;
      List<MenuResponse> children = tree(menuRecord.id(), nodes, allowed, navigation);
      if (navigation
          && (!menuRecord.visible()
              || !menuRecord.enabled()
              || "button".equals(menuRecord.menuType())
              || (!allowed.contains(menuRecord.id()) && children.isEmpty()))) continue;
      result.add(
          new MenuResponse(
              menuRecord.id(),
              menuRecord.parentId(),
              menuRecord.menuName(),
              menuRecord.menuType(),
              menuRecord.permissionCode(),
              menuRecord.routePath(),
              menuRecord.componentPath(),
              menuRecord.iconName(),
              menuRecord.visible(),
              menuRecord.enabled(),
              menuRecord.sortNo(),
              children));
    }
    return List.copyOf(result);
  }
}
