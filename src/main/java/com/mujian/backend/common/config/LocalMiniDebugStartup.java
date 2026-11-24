package com.mujian.backend.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 共享开发后端就绪后，交给现有开发工具检查手机预览；业务启动不等待手机服务。 */
@Component
@Profile("local & !test & !prod & !production")
@ConditionalOnProperty(name = "mj.environment", havingValue = "development")
public class LocalMiniDebugStartup {
  private static final Logger LOG = LoggerFactory.getLogger(LocalMiniDebugStartup.class);

  @EventListener
  public void onReady(ApplicationReadyEvent event) {
    if (!Boolean.getBoolean("mj.mini-debug.maven")
        || !(event.getApplicationContext() instanceof WebServerApplicationContext web)) return;
    Path script = Path.of("deploy", "mini-debug", "switch.py");
    if (!Files.isRegularFile(script)
        || !Files.isRegularFile(Path.of("../mujian-frontend/miniprogram/config/development.js"))) return;
    try {
      Process child = new ProcessBuilder("python3", script.toString(), "--ensure-phone",
          Integer.toString(web.getWebServer().getPort())).inheritIO().start();
      child.onExit().thenAccept(done -> {
        if (done.exitValue() != 0) LOG.warn("手机测试账号服务未就绪，请查看上方开发工具提示。");
      });
    } catch (IOException e) {
      LOG.warn("无法启动手机调试工具：{}", e.getMessage());
    }
  }
}
