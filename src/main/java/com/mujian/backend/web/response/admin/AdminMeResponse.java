package com.mujian.backend.web.response.admin;

import java.util.List;

public record AdminMeResponse(
    String userId,
    String adminAccountId,
    String username,
    String nickname,
    List<Role> roles,
    List<String> permissions) {
  public record Role(String roleCode, String roleName) {}
}
