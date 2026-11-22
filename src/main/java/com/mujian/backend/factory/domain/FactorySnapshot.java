package com.mujian.backend.factory.domain;

import java.util.List;

/** 不可变持久快照；仅领域/Mapper使用，接口使用定位符DTO投影。 */
public record FactorySnapshot(
    String factory_name,
    String unified_social_credit_code,
    String factory_avatar_url,
    String business_license_url,
    List<String> factory_photo_urls,
    String contact_name,
    String contact_phone,
    String factory_address,
    String main_business,
    Integer worker_count,
    List<String> specialty_category_ids_json,
    List<String> specialty_category_names_snapshot_json,
    List<ServiceCase> case_materials_json,
    String remark) {
  public record ServiceCase(String title, String description, List<String> image_object_keys) {}
}
