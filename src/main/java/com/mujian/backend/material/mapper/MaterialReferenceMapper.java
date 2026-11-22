package com.mujian.backend.material.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface MaterialReferenceMapper {
  /** 跨既有材料来源核对该用户对象是否仍被引用。 */
  default int countMaterialReferencesByOwnerAndKey(String owner, String key) {
    if(key.startsWith(com.mujian.backend.infrastructure.storage.CosMaterialStore.CUSTOM_PREFIX))return countCustomReferences(owner,key);
    if (!key.startsWith(
        com.mujian.backend.infrastructure.storage.CosMaterialStore.ORDER_EVIDENCE_PREFIX))
      return countPriorMaterialReferences(owner, key);
    int active = countOrderEvidenceReferences(owner, key);
    return active > 0 || !hasExpenseStatements()
        ? active
        : countReusedExpenseReferences(owner, key);
  }

  /** 按 owner, key 统计 custom_material 记录。 */
  int countCustomReferences(@Param("owner") String owner,@Param("key") String key);

  /** 维持原有材料用途的引用规则。 */
  int countPriorMaterialReferences(@Param("owner") String owner, @Param("key") String key);

  /** 活动/正式订单证据引用。 */
  int countOrderEvidenceReferences(@Param("owner") String owner, @Param("key") String key);

  /** A批独立运行时尚无费用表，B批后读取历史复用。 */
  boolean hasExpenseStatements();

  /** 返回历史申报版对原证据的引用数。 */
  int countReusedExpenseReferences(@Param("owner") String owner, @Param("key") String key);
}
