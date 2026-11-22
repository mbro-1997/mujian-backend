package com.mujian.backend.common.dictionary.mapper;

import com.mujian.backend.common.dictionary.DictionaryOptionKey;
import com.mujian.backend.common.dictionary.projection.DictionaryRecordProjection;
import com.mujian.backend.common.dictionary.projection.OptionDictionaryProjection;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.projection.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OptionDictionaryMapper {
  /** 按组批量读取字典，保留停用选项用于历史回显。 */
  List<OptionDictionaryProjection> selectOptionDictionaryListByGroups(
      @Param("groups") List<String> groups);

  /** 按既有排序批量读取启用项，供公共内存缓存加载。 */
  List<OptionDictionaryProjection> selectEnabledOptionDictionaryListByGroups(
      @Param("groups") List<String> groups);

  /** 按组和编码批量读取实际状态，供旧值回显和写入校验。 */
  List<OptionDictionaryProjection> selectOptionDictionaryListByKeys(
      @Param("keys") List<DictionaryOptionKey> dictionaryOptionKeys);

  /** 按编号批量读取道具类别并加共享锁，供工厂提交校验。 */
  List<DictionaryRecordProjection> selectPropTypeOptionsByIdsForShare(
      @Param("ids") List<String> ids);

  /** 按字典组和选项编码读取完整选项记录。 */
  List<DictionaryRecordProjection> selectDictionaryRecordByGroupAndCode(
      @Param("dictionaryGroup") String dictionaryGroup, @Param("optionCode") String optionCode);

  /** 按既定排序读取字典组的全部选项，保留启停状态。 */
  List<DictionaryRecordProjection> selectDictionaryRecordsByGroup(@Param("group") String group);
}
