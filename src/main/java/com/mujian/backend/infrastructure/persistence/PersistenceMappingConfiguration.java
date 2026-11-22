package com.mujian.backend.infrastructure.persistence;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/** 保留已有空值映射规则；已迁出的通用行包装器不再注册。 */
@Configuration
public class PersistenceMappingConfiguration {
  /** 保持显式null setter和空行实例语义。 */
  @Bean
  ConfigurationCustomizer verificationProjectionMapping() {
    return configuration -> {
      configuration.setCallSettersOnNulls(true);
      configuration.setReturnInstanceForEmptyRow(true);
    };
  }
}
