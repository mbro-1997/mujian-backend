package com.mujian.backend.material.service;

import org.springframework.web.context.request.RequestAttributes;

import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.account.service.AccountContextService;
import com.mujian.backend.application.service.admin.AdminAccessService;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.web.response.account.ContextResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.List;

@Service
public class VerificationAccess {
  private final AccountContextService accountContextService;
  private final AdminAccessService adminAccessService;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public VerificationAccess(
      AccountContextService accountContextService, AdminAccessService adminAccessService) {
    this.accountContextService = accountContextService;
    this.adminAccessService = adminAccessService;
  }

  /** 核对业务用户与材料归属。 */
  public boolean owner(String user, String domain, boolean write) {
    ContextResponse contextResponse = accountContextService.context(user);
    if (!CODE_DEMANDER.equals(contextResponse.roleCode()))
      throw ApiFailure.denied(domain.toUpperCase() + "_ROLE_NOT_ELIGIBLE");
    boolean active = CODE_ACTIVE.equals(contextResponse.userStatus());
    if (write && !active) throw ApiFailure.denied(CODE_ACCOUNT_ACTION_RESTRICTED);
    return active;
  }

  /** 核对管理员对应业务权限。 */
  public void admin(String actor, String permission) {
    if (!has(actor, permission)) throw ApiFailure.denied(CODE_PERMISSION_DENIED);
  }

  /** 列表和详情沿用 D1 已验证的本次管理员身份与权限。非 HTTP 调用读取当前授权。 */
  public List<String> readPermissions(String actor, String required) {
    RequestAttributes request = RequestContextHolder.getRequestAttributes();
    List<String> permissions;
    if (request instanceof ServletRequestAttributes http
        && http.getRequest().getAttribute("adminAccess") instanceof AdminAccessService.Access access) {
      if (!actor.equals(access.me().userId())) throw ApiFailure.denied(CODE_PERMISSION_DENIED);
      permissions = access.me().permissions();
    } else {
      permissions = adminAccessService.load(actor).me().permissions();
    }
    if (!permissions.contains(required)) throw ApiFailure.denied(CODE_PERMISSION_DENIED);
    return permissions;
  }

  /** 判断当前管理员是否具有指定操作权限。 */
  public boolean has(String actor, String permission) {
    return adminAccessService.load(actor).me().permissions().contains(permission);
  }
}
