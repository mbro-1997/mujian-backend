package com.mujian.backend.account.mapper;

import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.projection.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PlatformRuleDocumentMapper {
  /** 一次读取本批提交使用的已生效规则，正文版本随后随批次冻结。 */
  List<com.mujian.backend.account.projection.TradeRuleProjection> selectActiveTradeRules(
      @Param("codes") Set<String> codes, @Param("now") java.time.LocalDateTime now);

  /** 共享锁读取当前生效的工厂必读规则。 */
  List<FactoryRuleProjection> selectRequiredSupplierRulesForShare(
      @Param("codes") Set<String> codes);

  /** 按编号和允许的规则编码读取已生效文档。 */
  List<FactoryRuleProjection> selectSupplierRuleById(
      @Param("id") String id,
      @Param("codes") Set<String> codes,
      @Param("now") java.time.LocalDateTime now);

  /** 批量读取申请所保存的历史规则文档。 */
  List<FactoryRuleProjection> selectAcceptedRulesByIds(@Param("ids") List<String> ids);
}
