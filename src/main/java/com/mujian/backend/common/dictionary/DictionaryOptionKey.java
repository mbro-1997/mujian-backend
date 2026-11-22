package com.mujian.backend.common.dictionary;

import com.mujian.backend.common.enums.DictionaryGroup;

/** 一次批量读取中的字典组与选项编码，不保存字典内容。 */
public record DictionaryOptionKey(String groupCode, String code) {
  /** 从统一枚举构建SQL直接绑定的组编码。 */
  public DictionaryOptionKey(DictionaryGroup dictionaryGroup, String code) {
    this(dictionaryGroup.code(), code);
  }

  /** 取得启用快照使用的字典组。 */
  public DictionaryGroup group() {
    return DictionaryGroup.fromCode(groupCode);
  }
}
