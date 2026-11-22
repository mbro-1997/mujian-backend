package com.mujian.backend.custom.mapper;

import com.mujian.backend.custom.dto.CustomForms.*;
import java.time.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CustomMaterialMapper {
  record File(
      String id,
      String owner,
      String requirementId,
      String orderId,
      String purpose,
      String digest,
      String storageKey,
      String name,
      String type,
      LocalDateTime createdAt) {}

  /** 按 owner, key 查询 custom_material 记录。 */
  File byKey(@Param("owner") String owner, @Param("key") String key);

  /** 按 id 查询 custom_material 记录。 */
  File selectCustomMaterialById(@Param("id") String id);

  /** 按 orders 查询 custom_material 记录。 */
  List<File> orderFiles(@Param("orders") List<String> orders);

  /** 按 id, root 查询 custom_requirement_attachment 记录。 */
  boolean onRequirement(@Param("id") String id, @Param("root") String root);

  /** 按 key, root 查询 custom_delivery_record 记录。 */
  boolean onOrder(@Param("key") String key, @Param("root") String root);

  /** 按 key, root 查询 custom_after_sale_factory_statement 记录。 */
  boolean onFactoryStatement(@Param("key") String key, @Param("root") String root);

  /** 按 requirement, order, extension 查询 custom_order 记录。 */
  boolean adminScope(
      @Param("requirement") String requirement,
      @Param("order") String order,
      @Param("extension") boolean extension);

  /** 按 id, owner, requirement 新增 custom_material 记录。 */
  int insertCustomMaterial(
      @Param("id") String id,
      @Param("owner") String owner,
      @Param("requirement") String requirement,
      @Param("order") String order,
      @Param("purpose") String purpose,
      @Param("key") String key,
      @Param("digest") String digest,
      @Param("storage") String storage,
      @Param("name") String name,
      @Param("type") String type,
      @Param("at") LocalDateTime at);
}
