package com.mujian.backend.prop.service.impl;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.enums.DictionaryGroup;
import com.mujian.backend.common.enums.PropAssetType;
import com.mujian.backend.common.enums.PropAvailabilityReason;
import com.mujian.backend.common.enums.PropRentableStatus;
import com.mujian.backend.prop.converter.PropCardConverter;
import com.mujian.backend.prop.dto.PropBrowseQueryDTO;
import com.mujian.backend.prop.mapper.PropSkuMapper;
import com.mujian.backend.prop.mapper.PropSkuStyleMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.policy.PropAvailability;
import com.mujian.backend.prop.policy.PropAvailability.Dates;
import com.mujian.backend.prop.projection.PropBrowseProjection;
import com.mujian.backend.prop.projection.PropStyleProjection;
import com.mujian.backend.prop.service.PropBrowseService;
import com.mujian.backend.prop.service.PropCatalogService;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.vo.AvailabilityVO;
import com.mujian.backend.prop.vo.OptionVO;
import com.mujian.backend.prop.vo.PropCardVO;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import com.mujian.backend.prop.vo.RentableCardVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PropBrowseServiceImpl implements PropBrowseService {
  private final PropSkuMapper propSkuMapper;
  private final PropSkuStyleMapper propSkuStyleMapper;
  private final PropCatalogService propCatalogService;
  private final PropAccess propAccess;
  private final PropAvailability propAvailability;
  private final PropMaterialService propMaterialService;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropBrowseServiceImpl(
      PropSkuMapper propSkuMapper,
      PropSkuStyleMapper propSkuStyleMapper,
      PropCatalogService propCatalogService,
      PropAccess propAccess,
      PropAvailability propAvailability,
      PropMaterialService propMaterialService) {
    this.propSkuMapper = propSkuMapper;
    this.propSkuStyleMapper = propSkuStyleMapper;
    this.propCatalogService = propCatalogService;
    this.propAccess = propAccess;
    this.propAvailability = propAvailability;
    this.propMaterialService = propMaterialService;
  }

  /** 查询公开道具列表并批量组装展示数据。 */
  @Override
  public PageResult<PropCardVO> browse(PropBrowseQueryDTO propBrowseQueryDTO) {
    if (propBrowseQueryDTO.keyword() != null
        || propBrowseQueryDTO.startDate() != null
        || propBrowseQueryDTO.endDate() != null) throw ApiFailure.invalid();
    // 本次业务始终使用同一份启用字典快照，不在请求中重新加载缓存。
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = propCatalogService.snapshot();
    // 保持数据库分页、筛选与排序，越界页不查询明细。
    long total = propSkuMapper.selectPropCountByQuery(propBrowseQueryDTO);
    List<PropBrowseProjection> propBrowseProjectionList =
        propBrowseQueryDTO.offset() >= total
            ? List.of()
            : propSkuMapper.selectPropPageByQuery(propBrowseQueryDTO);
    // 批量读取当前页风格关联；名称直接从同一字典快照转换。
    List<String> skuIds = new ArrayList<>();
    for (PropBrowseProjection propBrowseProjection : propBrowseProjectionList) {
      skuIds.add(propBrowseProjection.skuId());
    }
    List<PropStyleProjection> propStyleProjectionList =
        skuIds.isEmpty() ? List.of() : propSkuStyleMapper.selectPropSkuStyleListBySkuIds(skuIds);
    Map<String, List<OptionVO>> optionVOListMap = new HashMap<>();
    for (PropStyleProjection propStyleProjection : propStyleProjectionList) {
      OptionVO optionVO =
          propCatalogService.displayOption(
              dictionarySnapshot, DictionaryGroup.PROP_SKU_STYLE, propStyleProjection.styleCode());
      List<OptionVO> optionVOList = optionVOListMap.get(propStyleProjection.skuId());
      if (optionVOList == null) {
        optionVOList = new ArrayList<>();
        optionVOListMap.put(propStyleProjection.skuId(), optionVOList);
      }
      optionVOList.add(optionVO);
    }
    // 封面同路径只签一次，不改变签名期限，不生成工厂头像签名。
    Map<String, PublicImageViewVO> publicImageViewVOMap = new HashMap<>();
    List<PropCardVO> propCardVOList = new ArrayList<>();
    for (PropBrowseProjection propBrowseProjection : propBrowseProjectionList) {
      if (propBrowseProjection.imageKey() != null
          && !publicImageViewVOMap.containsKey(propBrowseProjection.imageKey())) {
        publicImageViewVOMap.put(
            propBrowseProjection.imageKey(),
            propMaterialService.publicImage(propBrowseProjection.imageKey()));
      }
      OptionVO optionVOType =
          propCatalogService.displayOption(
              dictionarySnapshot, DictionaryGroup.PROP_SKU_TYPE, propBrowseProjection.typeCode());
      propCardVOList.add(
          PropCardConverter.card(
              propBrowseProjection,
              optionVOType,
              optionVOListMap.getOrDefault(propBrowseProjection.skuId(), List.of()),
              publicImageViewVOMap.get(propBrowseProjection.imageKey())));
    }
    return PageResult.of(propCardVOList, total, propBrowseQueryDTO.paging());
  }

  /** 校验租赁方身份后查询匹配道具。 */
  @Override
  public PageResult<PropCardVO> search(String actor, PropBrowseQueryDTO propBrowseQueryDTO) {
    if (actor == null) throw ApiFailure.unauthorized();
    propAccess.demander(actor);
    if (propBrowseQueryDTO.startDate() != null || propBrowseQueryDTO.endDate() != null)
      throw ApiFailure.invalid();
    // 本次业务始终使用同一份启用字典快照，不在请求中重新加载缓存。
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = propCatalogService.snapshot();
    // 保持数据库分页、筛选与排序，越界页不查询明细。
    long total = propSkuMapper.selectPropCountByQuery(propBrowseQueryDTO);
    List<PropBrowseProjection> propBrowseProjectionList =
        propBrowseQueryDTO.offset() >= total
            ? List.of()
            : propSkuMapper.selectPropPageByQuery(propBrowseQueryDTO);
    // 批量读取当前页风格关联；名称直接从同一字典快照转换。
    List<String> skuIds = new ArrayList<>();
    for (PropBrowseProjection propBrowseProjection : propBrowseProjectionList) {
      skuIds.add(propBrowseProjection.skuId());
    }
    List<PropStyleProjection> propStyleProjectionList =
        skuIds.isEmpty() ? List.of() : propSkuStyleMapper.selectPropSkuStyleListBySkuIds(skuIds);
    Map<String, List<OptionVO>> optionVOListMap = new HashMap<>();
    for (PropStyleProjection propStyleProjection : propStyleProjectionList) {
      OptionVO optionVO =
          propCatalogService.displayOption(
              dictionarySnapshot, DictionaryGroup.PROP_SKU_STYLE, propStyleProjection.styleCode());
      List<OptionVO> optionVOList = optionVOListMap.get(propStyleProjection.skuId());
      if (optionVOList == null) {
        optionVOList = new ArrayList<>();
        optionVOListMap.put(propStyleProjection.skuId(), optionVOList);
      }
      optionVOList.add(optionVO);
    }
    // 封面同路径只签一次，不改变签名期限，不生成工厂头像签名。
    Map<String, PublicImageViewVO> publicImageViewVOMap = new HashMap<>();
    List<PropCardVO> propCardVOList = new ArrayList<>();
    for (PropBrowseProjection propBrowseProjection : propBrowseProjectionList) {
      if (propBrowseProjection.imageKey() != null
          && !publicImageViewVOMap.containsKey(propBrowseProjection.imageKey())) {
        publicImageViewVOMap.put(
            propBrowseProjection.imageKey(),
            propMaterialService.publicImage(propBrowseProjection.imageKey()));
      }
      OptionVO optionVOType =
          propCatalogService.displayOption(
              dictionarySnapshot, DictionaryGroup.PROP_SKU_TYPE, propBrowseProjection.typeCode());
      propCardVOList.add(
          PropCardConverter.card(
              propBrowseProjection,
              optionVOType,
              optionVOListMap.getOrDefault(propBrowseProjection.skuId(), List.of()),
              publicImageViewVOMap.get(propBrowseProjection.imageKey())));
    }
    return PageResult.of(propCardVOList, total, propBrowseQueryDTO.paging());
  }

  /** 计算全体候选的租期可租量，完成全局排序后分页。 */
  @Override
  public PageResult<RentableCardVO> rentable(String actor, PropBrowseQueryDTO propBrowseQueryDTO) {
    if (actor == null) throw ApiFailure.unauthorized();
    propAccess.demander(actor);
    Dates dates =
        propAvailability.dates(propBrowseQueryDTO.startDate(), propBrowseQueryDTO.endDate(), false);
    if (dates == null) throw ApiFailure.invalid();
    // 本次业务始终使用同一份启用字典快照，不在请求中重新加载缓存。
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = propCatalogService.snapshot();
    List<PropBrowseProjection> propBrowseProjectionListAll =
        new ArrayList<>(propSkuMapper.selectPropCandidateListByQuery(propBrowseQueryDTO));
    List<String> skuIdsAll = new ArrayList<>();
    for (PropBrowseProjection propBrowseProjection : propBrowseProjectionListAll)
      skuIdsAll.add(propBrowseProjection.skuId());
    // 仍计算全体候选后稳定排序，不先LIMIT；保留原created_at/id顺序。
    Map<String, Integer> quantities = propAvailability.quantities(skuIdsAll, dates);
    propBrowseProjectionListAll.sort(
        Comparator.comparingInt(
            propBrowseProjection ->
                quantities.getOrDefault(propBrowseProjection.skuId(), 0) > 0 ? 0 : 1));
    long total = propBrowseProjectionListAll.size();
    long offset = propBrowseQueryDTO.offset();
    List<PropBrowseProjection> propBrowseProjectionList =
        offset >= total
            ? List.of()
            : propBrowseProjectionListAll.subList(
                (int) offset, (int) Math.min(total, offset + propBrowseQueryDTO.pageSize()));
    // 批量读取当前页风格关联；名称直接从同一字典快照转换。
    List<String> skuIds = new ArrayList<>();
    for (PropBrowseProjection propBrowseProjection : propBrowseProjectionList) {
      skuIds.add(propBrowseProjection.skuId());
    }
    List<PropStyleProjection> propStyleProjectionList =
        skuIds.isEmpty() ? List.of() : propSkuStyleMapper.selectPropSkuStyleListBySkuIds(skuIds);
    Map<String, List<OptionVO>> optionVOListMap = new HashMap<>();
    for (PropStyleProjection propStyleProjection : propStyleProjectionList) {
      OptionVO optionVO =
          propCatalogService.displayOption(
              dictionarySnapshot, DictionaryGroup.PROP_SKU_STYLE, propStyleProjection.styleCode());
      List<OptionVO> optionVOList = optionVOListMap.get(propStyleProjection.skuId());
      if (optionVOList == null) {
        optionVOList = new ArrayList<>();
        optionVOListMap.put(propStyleProjection.skuId(), optionVOList);
      }
      optionVOList.add(optionVO);
    }
    // 封面同路径只签一次，不改变签名期限，不生成工厂头像签名。
    Map<String, PublicImageViewVO> publicImageViewVOMap = new HashMap<>();
    List<PropCardVO> propCardVOList = new ArrayList<>();
    for (PropBrowseProjection propBrowseProjection : propBrowseProjectionList) {
      if (propBrowseProjection.imageKey() != null
          && !publicImageViewVOMap.containsKey(propBrowseProjection.imageKey())) {
        publicImageViewVOMap.put(
            propBrowseProjection.imageKey(),
            propMaterialService.publicImage(propBrowseProjection.imageKey()));
      }
      OptionVO optionVOType =
          propCatalogService.displayOption(
              dictionarySnapshot, DictionaryGroup.PROP_SKU_TYPE, propBrowseProjection.typeCode());
      propCardVOList.add(
          PropCardConverter.card(
              propBrowseProjection,
              optionVOType,
              optionVOListMap.getOrDefault(propBrowseProjection.skuId(), List.of()),
              publicImageViewVOMap.get(propBrowseProjection.imageKey())));
    }
    ArrayList<RentableCardVO> rentableCardVOList = new ArrayList<RentableCardVO>();
    for (int i = 0; i < propBrowseProjectionList.size(); i++) {
      PropBrowseProjection propBrowseProjectionSelected = propBrowseProjectionList.get(i);
      int quantity = quantities.getOrDefault(propBrowseProjectionSelected.skuId(), 0);
      AvailabilityVO availabilityVO =
          new AvailabilityVO()
              .startDate(dates.start().toString())
              .endDate(dates.end().toString())
              .maxRentableQuantity(quantity)
              .rentableStatus(
                  (quantity > 0 ? PropRentableStatus.RENTABLE : PropRentableStatus.UNAVAILABLE)
                      .code())
              .reasonCode(
                  quantity > 0
                      ? null
                      : PropAssetType.A_ASSET
                              .name()
                              .equals(propBrowseProjectionSelected.assetType())
                          ? PropAvailabilityReason.SN_NOT_AVAILABLE.name()
                          : PropAvailabilityReason.STOCK_NOT_ENOUGH.name());
      rentableCardVOList.add(
          new RentableCardVO().prop(propCardVOList.get(i)).availability(availabilityVO));
    }
    return PageResult.of(rentableCardVOList, total, propBrowseQueryDTO.paging());
  }
}
