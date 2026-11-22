package com.mujian.backend.prop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.dictionary.DictionaryCache;
import com.mujian.backend.common.dictionary.mapper.OptionDictionaryMapper;
import com.mujian.backend.common.dictionary.projection.OptionDictionaryProjection;
import com.mujian.backend.common.enums.DictionaryGroup;
import com.mujian.backend.prop.service.impl.PropCatalogServiceImpl;
import com.mujian.backend.prop.vo.OptionVO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PropCatalogCacheTest {
  /** 命中与未命中都只读快照，保留原code且不查库、不刷新。 */
  @Test
  void displayUsesSnapshotOnlyAndMissingValuesAreDisabled() {
    OptionDictionaryMapper optionDictionaryMapper = mock(OptionDictionaryMapper.class);
    DictionaryCache dictionaryCache = mock(DictionaryCache.class);
    PropCatalogServiceImpl propCatalogServiceImpl =
        new PropCatalogServiceImpl(dictionaryCache, optionDictionaryMapper);
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot =
        Map.of(
            DictionaryGroup.PROP_SKU_TYPE, Map.of("chair", "椅子"),
            DictionaryGroup.PROP_SKU_STYLE, Map.of("modern", "现代"));
    OptionVO optionVO =
        propCatalogServiceImpl.displayOption(
            dictionarySnapshot, DictionaryGroup.PROP_SKU_TYPE, "chair");
    assertEquals("chair", optionVO.getCode());
    assertEquals("椅子", optionVO.getLabel());
    assertTrue(optionVO.getEnabled());
    assertFalse(optionVO.getRetained());
    for (String code : List.of("retro", "unknown")) {
      optionVO =
          propCatalogServiceImpl.displayOption(
              dictionarySnapshot, DictionaryGroup.PROP_SKU_STYLE, code);
      assertEquals(code, optionVO.getCode());
      assertEquals("已停用", optionVO.getLabel());
      assertFalse(optionVO.getEnabled());
      assertTrue(optionVO.getRetained());
      assertFalse(dictionarySnapshot.get(DictionaryGroup.PROP_SKU_STYLE).containsKey(code));
    }
    verifyNoInteractions(optionDictionaryMapper, dictionaryCache);
  }

  /** 写入继续先校验类型状态，再校验风格数量与特殊资产互斥。 */
  @Test
  void writeValidationPreservesErrorPriorityAndStyleRules() {
    OptionDictionaryMapper optionDictionaryMapper = mock(OptionDictionaryMapper.class);
    PropCatalogServiceImpl propCatalogServiceImpl =
        new PropCatalogServiceImpl(mock(DictionaryCache.class), optionDictionaryMapper);
    when(optionDictionaryMapper.selectOptionDictionaryListByKeys(anyList()))
        .thenReturn(List.of(new OptionDictionaryProjection("prop_sku-type", "chair", "椅子", 0)));
    ApiFailure apiFailure =
        assertThrows(
            ApiFailure.class,
            () ->
                propCatalogServiceImpl.validateSelection(
                    "chair", null, List.of(), List.of(), List.of()));
    assertEquals("DICTIONARY_OPTION_DISABLED", apiFailure.reason);
    when(optionDictionaryMapper.selectOptionDictionaryListByKeys(anyList()))
        .thenReturn(List.of(new OptionDictionaryProjection("prop_sku-type", "chair", "椅子", 1)));
    apiFailure =
        assertThrows(
            ApiFailure.class,
            () ->
                propCatalogServiceImpl.validateSelection(
                    "chair", null, List.of("special_asset", "modern"), List.of(), List.of()));
    assertEquals("REQUEST_INVALID", apiFailure.reason);
  }
}
