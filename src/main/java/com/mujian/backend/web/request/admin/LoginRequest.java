package com.mujian.backend.web.request.admin;

public record LoginRequest(String username, String password,String captchaId,String captchaAnswer) {}
