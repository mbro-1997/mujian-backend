package com.mujian.backend.planning.service;

import static com.mujian.backend.planning.utils.PlanningValues.inconsistent;

import com.mujian.backend.common.enums.DictionaryGroup;
import com.mujian.backend.planning.domain.SnapshotV1;
import com.mujian.backend.planning.vo.PlanningViews.Prices;
import com.mujian.backend.prop.domain.*;
import com.mujian.backend.prop.mapper.*;
import com.mujian.backend.prop.projection.*;
import com.mujian.backend.prop.service.PropCatalogService;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 批量读取可信商品资料；不复制库存算法，不逐SKU查询。 */
@Service
public class PlanningSnapshots {
  private final PropSkuMapper propSkuMapper;
  private final PropSkuStyleMapper propSkuStyleMapper;
  private final PropImageMapper propImageMapper;
  private final PropCatalogService propCatalogService;
  private final ObjectMapper objectMapper;

  public record Current(SnapshotV1 snapshot, boolean publicVisible) {}

  /** 装配既有批量Mapper和字典快照能力。 */
  public PlanningSnapshots(
      PropSkuMapper propSkuMapper,
      PropSkuStyleMapper propSkuStyleMapper,
      PropImageMapper propImageMapper,
      PropCatalogService propCatalogService,
      ObjectMapper objectMapper) {
    this.propSkuMapper = propSkuMapper;
    this.propSkuStyleMapper = propSkuStyleMapper;
    this.propImageMapper = propImageMapper;
    this.propCatalogService = propCatalogService;
    this.objectMapper = objectMapper;
  }

  /** 一致读取当前批量展示资料；分批只限制SQL参数，不限制业务条数。 */
  public Map<String, Current> current(Collection<String> ids) {
    Map<String, Current> currentMapResult = new HashMap<>();
    List<String> skuIds = new ArrayList<>(new LinkedHashSet<>(ids));
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = propCatalogService.snapshot();
    for (int start = 0; start < skuIds.size(); start += 500) {
      List<String> batch = skuIds.subList(start, Math.min(start + 500, skuIds.size()));
      Map<String, PropPlanningProjection> propPlanningProjectionMapFacts = new HashMap<>();
      for (PropPlanningProjection propPlanningProjectionFact :
          propSkuMapper.selectPlanningFactsBySkuIds(batch))
        propPlanningProjectionMapFacts.put(
            propPlanningProjectionFact.skuId(), propPlanningProjectionFact);
      Map<String, List<String>> styles = new HashMap<>(), images = new HashMap<>();
      for (PropStyleProjection propStyleProjectionStyle :
          propSkuStyleMapper.selectPropSkuStyleListBySkuIds(batch)) {
        List<String> values = styles.get(propStyleProjectionStyle.skuId());
        if (values == null) {
          values = new ArrayList<>();
          styles.put(propStyleProjectionStyle.skuId(), values);
        }
        values.add(propStyleProjectionStyle.styleCode());
      }
      for (PropImageDO propImageDOImage : propImageMapper.selectPropImageListBySkuIds(batch)) {
        List<String> values = images.get(propImageDOImage.getSkuId());
        if (values == null) {
          values = new ArrayList<>();
          images.put(propImageDOImage.getSkuId(), values);
        }
        values.add(propImageDOImage.getImageUrl());
      }
      for (PropSkuDO propSkuDO : propSkuMapper.selectPropSkuListByIds(batch)) {
        PropPlanningProjection propPlanningProjectionFact =
            propPlanningProjectionMapFacts.get(propSkuDO.getId());
        if (propPlanningProjectionFact == null) throw inconsistent();
        List<String> codes = styles.getOrDefault(propSkuDO.getId(), List.of());
        List<String> labels = new ArrayList<>();
        for (String code : codes)
          labels.add(
              propCatalogService
                  .displayOption(dictionarySnapshot, DictionaryGroup.PROP_SKU_STYLE, code)
                  .getLabel());
        SnapshotV1 snapshotV1 =
            new SnapshotV1(
                1,
                propSkuDO.getId(),
                propPlanningProjectionFact.factoryId(),
                propPlanningProjectionFact.factoryName(),
                propSkuDO.getPropName(),
                propSkuDO.getAssetType(),
                propSkuDO.getTypeCode(),
                propCatalogService
                    .displayOption(
                        dictionarySnapshot, DictionaryGroup.PROP_SKU_TYPE, propSkuDO.getTypeCode())
                    .getLabel(),
                codes,
                labels,
                propSkuDO.getSizeDesc(),
                propSkuDO.getLengthCm().toPlainString(),
                propSkuDO.getWidthCm().toPlainString(),
                propSkuDO.getHeightCm().toPlainString(),
                propSkuDO.getWeightKg().toPlainString(),
                propSkuDO.getMaterialDesc(),
                images.getOrDefault(propSkuDO.getId(), List.of()),
                new Prices(propSkuDO.getDailyRentPriceFen(), propSkuDO.getDepositAmountFen()));
        currentMapResult.put(
            snapshotV1.skuId(),
            new Current(snapshotV1, propPlanningProjectionFact.publicVisible()));
      }
    }
    return currentMapResult;
  }

  /** 读取冻结快照并核对其根归属，不返回存储键到客户端。 */
  public SnapshotV1 read(String raw, String skuId, String factoryId) {
    try {
      SnapshotV1 snapshotV1 = objectMapper.readValue(raw, SnapshotV1.class);
      if (snapshotV1.schemaVersion() != 1
          || !skuId.equals(snapshotV1.skuId())
          || !factoryId.equals(snapshotV1.factoryId())
          || snapshotV1.prices() == null) throw inconsistent();
      return snapshotV1;
    } catch (tools.jackson.core.JacksonException jacksonExceptionException) {
      throw inconsistent().causedBy(jacksonExceptionException);
    }
  }

  /** 序列化服务端白名单快照。 */
  public String write(SnapshotV1 snapshotV1) {
    return objectMapper.writeValueAsString(snapshotV1);
  }
}
