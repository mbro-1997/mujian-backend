package com.mujian.backend.prop.service.impl;

import com.mujian.backend.common.enums.BusinessFailureReason;
import static com.mujian.backend.common.support.D2Values.conflict;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.dictionary.DictionaryCache;
import com.mujian.backend.common.dictionary.DictionaryOptionKey;
import com.mujian.backend.common.dictionary.mapper.OptionDictionaryMapper;
import com.mujian.backend.common.dictionary.projection.OptionDictionaryProjection;
import com.mujian.backend.common.enums.DictionaryGroup;
import com.mujian.backend.prop.service.PropCatalogService;
import com.mujian.backend.prop.vo.OptionVO;
import com.mujian.backend.prop.vo.OptionsVO;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PropCatalogServiceImpl implements PropCatalogService {
  private final DictionaryCache dictionaryCache;
  private final OptionDictionaryMapper optionDictionaryMapper;

  /** 装配公共缓存与字典Mapper，不持有第二套字典缓存。 */
  public PropCatalogServiceImpl(
      DictionaryCache dictionaryCache, OptionDictionaryMapper optionDictionaryMapper) {
    this.dictionaryCache = dictionaryCache;
    this.optionDictionaryMapper = optionDictionaryMapper;
  }

  /** 返回业务内固定的不可变启用字典快照。 */
  public Map<DictionaryGroup, Map<String, String>> snapshot() {
    return dictionaryCache.snapshot();
  }

  /** 仅映射快照与展示约定；未命中不查库、不报错、不写回缓存。 */
  public OptionVO displayOption(
      Map<DictionaryGroup, Map<String, String>> dictionarySnapshot,
      DictionaryGroup dictionaryGroup,
      String code) {
    String label = dictionarySnapshot.get(dictionaryGroup).get(code);
    boolean enabled = label != null;
    return new OptionVO()
        .code(code)
        .label(enabled ? label : "已停用")
        .enabled(enabled)
        .retained(!enabled);
  }

  /** 写入以一次数据库读取为准，缓存刷新失败也不能放行停用的新选择。 */
  public Map<DictionaryOptionKey, OptionVO> validateSelection(
      String typeCode,
      String fireCode,
      List<String> styleCodes,
      Collection<String> retainedTypes,
      Collection<String> retainedStyles) {
    List<DictionaryOptionKey> dictionaryOptionKeys = new ArrayList<>();
    dictionaryOptionKeys.add(new DictionaryOptionKey(DictionaryGroup.PROP_SKU_TYPE, typeCode));
    if (fireCode != null)
      dictionaryOptionKeys.add(
          new DictionaryOptionKey(DictionaryGroup.PROP_SKU_FIRE_RESISTANT, fireCode));
    if (styleCodes != null) {
      for (String code : styleCodes)
        dictionaryOptionKeys.add(new DictionaryOptionKey(DictionaryGroup.PROP_SKU_STYLE, code));
    }
    Map<DictionaryOptionKey, OptionVO> optionVOMap = new LinkedHashMap<>();
    for (OptionDictionaryProjection optionDictionaryProjection :
        optionDictionaryMapper.selectOptionDictionaryListByKeys(dictionaryOptionKeys)) {
      DictionaryOptionKey dictionaryOptionKey =
          new DictionaryOptionKey(
              DictionaryGroup.fromCode(optionDictionaryProjection.optionGroup()),
              optionDictionaryProjection.code());
      optionVOMap.put(
          dictionaryOptionKey,
          new OptionVO()
              .code(optionDictionaryProjection.code())
              .label(optionDictionaryProjection.label())
              .enabled(optionDictionaryProjection.enabled() == 1)
              .retained(optionDictionaryProjection.enabled() != 1));
    }
    // 保留原校验顺序：类型、防火，再检查风格组合及状态。
    for (DictionaryOptionKey dictionaryOptionKey : dictionaryOptionKeys) {
      if (dictionaryOptionKey.group() == DictionaryGroup.PROP_SKU_STYLE) continue;
      OptionVO optionVO = optionVOMap.get(dictionaryOptionKey);
      if (optionVO == null) throw ApiFailure.invalid();
      boolean retained =
          dictionaryOptionKey.group() == DictionaryGroup.PROP_SKU_TYPE
              && retainedTypes.contains(dictionaryOptionKey.code());
      if (!Boolean.TRUE.equals(optionVO.getEnabled()) && !retained)
        throw conflict(BusinessFailureReason.DICTIONARY_OPTION_DISABLED.name());
    }
    if (styleCodes == null
        || styleCodes.isEmpty()
        || styleCodes.size() > 3
        || new HashSet<>(styleCodes).size() != styleCodes.size()
        || (styleCodes.contains("special_asset") && styleCodes.size() != 1))
      throw ApiFailure.invalid();
    for (String code : styleCodes) {
      OptionVO optionVO =
          optionVOMap.get(new DictionaryOptionKey(DictionaryGroup.PROP_SKU_STYLE, code));
      if (optionVO == null) throw ApiFailure.invalid();
      if (!Boolean.TRUE.equals(optionVO.getEnabled()) && !retainedStyles.contains(code))
        throw conflict(BusinessFailureReason.DICTIONARY_OPTION_DISABLED.name());
    }
    return optionVOMap;
  }

  /** 完整选项一次查询，保留原排序及enabled/retained响应字段。 */
  public OptionsVO options() {
    List<String> groups = new ArrayList<>();
    for (DictionaryGroup dictionaryGroup : DictionaryGroup.values())
      groups.add(dictionaryGroup.code());
    List<OptionVO> optionVOListTypes = new ArrayList<>();
    List<OptionVO> optionVOListStyles = new ArrayList<>();
    List<OptionVO> optionVOListFire = new ArrayList<>();
    for (OptionDictionaryProjection optionDictionaryProjection :
        optionDictionaryMapper.selectOptionDictionaryListByGroups(groups)) {
      OptionVO optionVO =
          new OptionVO()
              .code(optionDictionaryProjection.code())
              .label(optionDictionaryProjection.label())
              .enabled(optionDictionaryProjection.enabled() == 1)
              .retained(optionDictionaryProjection.enabled() != 1);
      DictionaryGroup dictionaryGroup =
          DictionaryGroup.fromCode(optionDictionaryProjection.optionGroup());
      if (dictionaryGroup == DictionaryGroup.PROP_SKU_TYPE) optionVOListTypes.add(optionVO);
      else if (dictionaryGroup == DictionaryGroup.PROP_SKU_STYLE) optionVOListStyles.add(optionVO);
      else if (dictionaryGroup == DictionaryGroup.PROP_SKU_FIRE_RESISTANT)
        optionVOListFire.add(optionVO);
    }
    return new OptionsVO()
        .types(optionVOListTypes)
        .styles(optionVOListStyles)
        .fireResistance(optionVOListFire);
  }
}
