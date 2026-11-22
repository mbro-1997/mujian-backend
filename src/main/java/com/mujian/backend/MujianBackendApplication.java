package com.mujian.backend;

import com.mujian.backend.common.dictionary.DictionaryCache;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(
    exclude = {
      org.springframework.boot.session.autoconfigure.SessionAutoConfiguration.class,
      org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration
          .class
    })
public class MujianBackendApplication {
  /** 启动现有幕间后端应用。 */
  public static void main(String[] args) {
    SpringApplication.run(MujianBackendApplication.class, args);
  }

  /** 数据库迁移完成后加载字典，失败则不完成应用启动。 */
  @Bean
  public ApplicationRunner dictionaryInitializer(DictionaryCache dictionaryCache) {
    return applicationArguments -> dictionaryCache.initialize();
  }
}
