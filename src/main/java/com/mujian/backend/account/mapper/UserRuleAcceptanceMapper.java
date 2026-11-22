package com.mujian.backend.account.mapper;

import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.projection.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserRuleAcceptanceMapper {

  /** 统计用户是否已接受指定工厂规则文档。 */
  long countSupplierRuleAcceptance(@Param("user") String user, @Param("doc") String doc);

  /** 保存用户接受的工厂规则版本及内容摘要。 */
  int insertSupplierRuleAcceptance(
      @Param("id") String id,
      @Param("user") String user,
      @Param("doc") String doc,
      @Param("code") String code,
      @Param("version") String version,
      @Param("hash") String hash,
      @Param("now") java.time.LocalDateTime now);
}
