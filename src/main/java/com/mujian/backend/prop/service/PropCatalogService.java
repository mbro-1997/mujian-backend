package com.mujian.backend.prop.service;

import com.mujian.backend.common.dictionary.DictionaryOptionKey;
import com.mujian.backend.common.enums.DictionaryGroup;
import com.mujian.backend.prop.vo.OptionVO;
import com.mujian.backend.prop.vo.OptionsVO;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 道具字典批量读取与有效性校验，复用唯一公共缓存。 */
public interface PropCatalogService {
  /** 获取当前业务使用的启用项快照。 */
  Map<DictionaryGroup, Map<String, String>> snapshot();

  /** 仅按快照转换展示字段，未命中固定显示已停用，不查库或刷新。 */
  OptionVO displayOption(
      Map<DictionaryGroup, Map<String, String>> dictionarySnapshot,
      DictionaryGroup dictionaryGroup,
      String code);

  /** 批量校验实际数据库状态，拒绝新选停用项并保留原有风格约束。 */
  Map<DictionaryOptionKey, OptionVO> validateSelection(
      String typeCode,
      String fireCode,
      List<String> styleCodes,
      Collection<String> retainedTypes,
      Collection<String> retainedStyles);

  /** 返回完整有序选项，包含停用状态用于管理编辑和旧值回显。 */
  OptionsVO options();
}
