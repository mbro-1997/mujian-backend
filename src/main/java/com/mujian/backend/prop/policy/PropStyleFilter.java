package com.mujian.backend.prop.policy;

import com.mujian.backend.common.api.ApiFailure;
import java.util.HashSet;
import java.util.List;

/** 查询风格只核对传参格式；不检查字典存在性、不限制SKU属性互斥。 */
public final class PropStyleFilter {
  /** 只提供统一查询格式校验。 */
  private PropStyleFilter() {}

  /** 保留重复query键语义，拒绝把逗号串或JSON数组当作一个编码。 */
  public static List<String> validate(List<String> codes) {
    if (codes == null) return null;
    if (codes.isEmpty() || codes.size() > 3 || new HashSet<>(codes).size() != codes.size())
      throw ApiFailure.invalid();
    for (String code : codes) {
      if (code == null
          || !code.matches("[!-~]{1,32}")
          || code.contains(",")
          || code.startsWith("[")) throw ApiFailure.invalid();
    }
    return List.copyOf(codes);
  }
}
