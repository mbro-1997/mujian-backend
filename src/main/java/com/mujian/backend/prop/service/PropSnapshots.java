package com.mujian.backend.prop.service;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.dictionary.DictionaryOptionKey;
import com.mujian.backend.common.enums.DictionaryGroup;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.prop.domain.PropImageDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSkuStyleDO;
import com.mujian.backend.prop.dto.PropSkuFieldsDTO;
import com.mujian.backend.prop.dto.PropSkuSnapshotDTO;
import com.mujian.backend.prop.dto.SkuInputDTO;
import com.mujian.backend.prop.mapper.PropImageMapper;
import com.mujian.backend.prop.mapper.PropSkuMapper;
import com.mujian.backend.prop.mapper.PropSkuStyleMapper;
import com.mujian.backend.prop.policy.PropAccess;
import com.mujian.backend.prop.utils.PropJson;
import com.mujian.backend.prop.vo.OptionVO;
import com.mujian.backend.prop.vo.SkuReadVO;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PropSnapshots {
  private final PropSkuMapper propSkuMapper;
  private final PropSkuStyleMapper propSkuStyleMapper;
  private final PropImageMapper propImageMapper;
  private final PropJson propJson;
  private final PropMaterialService propMaterialService;
  private final PropCatalogService propCatalogService;
  private final Clock clock;
  public static final List<String> FIELD_COLUMNS =
      List.of(
          "prop_name",
          "type_code",
          "size_desc",
          "length_cm",
          "width_cm",
          "height_cm",
          "material_desc",
          "fire_resistant_code",
          "weight_kg",
          "transport_suggestion",
          "daily_rent_price_fen",
          "deposit_amount_fen",
          "remark");

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropSnapshots(
      PropSkuMapper propSkuMapper,
      PropSkuStyleMapper propSkuStyleMapper,
      PropImageMapper propImageMapper,
      PropJson propJson,
      PropMaterialService propMaterialService,
      PropCatalogService propCatalogService,
      Clock clock) {
    this.propSkuMapper = propSkuMapper;
    this.propSkuStyleMapper = propSkuStyleMapper;
    this.propImageMapper = propImageMapper;
    this.propJson = propJson;
    this.propMaterialService = propMaterialService;
    this.propCatalogService = propCatalogService;
    this.clock = clock;
  }

  /** 按存储顺序读取道具风格编码。 */
  public List<String> styles(String sku) {
    List<String> styleCodes = new ArrayList<>();
    for (PropSkuStyleDO propSkuStyleDO : propSkuStyleMapper.selectPropSkuStyleListBySkuId(sku))
      styleCodes.add(propSkuStyleDO.getStyleCode());
    return styleCodes;
  }

  /** 按原顺序读取道具图片对象路径。 */
  public List<String> keys(String sku) {
    List<String> imageKeys = new ArrayList<>();
    for (PropImageDO propImageDO : propImageMapper.selectSnapshotPropImageListBySkuId(sku))
      imageKeys.add(propImageDO.getImageUrl());
    return imageKeys;
  }

  /** 读取当前生效的道具字段及关联风格和图片。 */
  public PropSkuFieldsDTO currentValues(PropSkuDO propSkuDO) {
    return currentValues(propSkuDO, styles(propSkuDO.getId()), keys(propSkuDO.getId()));
  }

  /** 将已批量读取的关联字段与道具属性合并，不再查库。 */
  public PropSkuFieldsDTO currentValues(
      PropSkuDO propSkuDO, List<String> styleCodes, List<String> imageKeys) {
    PropSkuFieldsDTO propSkuFieldsDTO = new PropSkuFieldsDTO();
    propSkuFieldsDTO.setPropName(propSkuDO.getPropName());
    propSkuFieldsDTO.setTypeCode(propSkuDO.getTypeCode());
    propSkuFieldsDTO.setSizeDesc(propSkuDO.getSizeDesc());
    propSkuFieldsDTO.setLengthCm(propSkuDO.getLengthCm().setScale(2).toPlainString());
    propSkuFieldsDTO.setWidthCm(propSkuDO.getWidthCm().setScale(2).toPlainString());
    propSkuFieldsDTO.setHeightCm(propSkuDO.getHeightCm().setScale(2).toPlainString());
    propSkuFieldsDTO.setMaterialDesc(propSkuDO.getMaterialDesc());
    propSkuFieldsDTO.setFireResistantCode(propSkuDO.getFireResistantCode());
    propSkuFieldsDTO.setWeightKg(propSkuDO.getWeightKg().setScale(2).toPlainString());
    propSkuFieldsDTO.setPackagingFactorBps(propSkuDO.getPackagingFactorBps());
    propSkuFieldsDTO.setTransportSuggestion(propSkuDO.getTransportSuggestion());
    propSkuFieldsDTO.setDailyRentPriceFen(propSkuDO.getDailyRentPriceFen());
    propSkuFieldsDTO.setDepositAmountFen(propSkuDO.getDepositAmountFen());
    propSkuFieldsDTO.setRemark(propSkuDO.getRemark());
    propSkuFieldsDTO.setStyleCodes(styleCodes);
    propSkuFieldsDTO.setImageKeys(imageKeys);
    return propSkuFieldsDTO;
  }

  /** 当前资料仅用启用快照显示名称，缺失编码显示已停用。 */
  public PropSkuFieldsDTO current(PropSkuDO propSkuDO) {
    return current(propSkuDO, styles(propSkuDO.getId()), keys(propSkuDO.getId()));
  }

  /** 对已准备字段补齐当前字典名称，不查询关联表。 */
  public PropSkuFieldsDTO current(
      PropSkuDO propSkuDO, List<String> styleCodes, List<String> imageKeys) {
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = propCatalogService.snapshot();
    PropSkuFieldsDTO propSkuFieldsDTO = currentValues(propSkuDO, styleCodes, imageKeys);
    propSkuFieldsDTO.setTypeLabel(
        propCatalogService
            .displayOption(
                dictionarySnapshot, DictionaryGroup.PROP_SKU_TYPE, propSkuFieldsDTO.getTypeCode())
            .getLabel());
    propSkuFieldsDTO.setFireResistantLabel(
        propCatalogService
            .displayOption(
                dictionarySnapshot,
                DictionaryGroup.PROP_SKU_FIRE_RESISTANT,
                propSkuFieldsDTO.getFireResistantCode())
            .getLabel());
    List<String> styleLabels = new ArrayList<>();
    for (String code : propSkuFieldsDTO.getStyleCodes()) {
      styleLabels.add(
          propCatalogService
              .displayOption(dictionarySnapshot, DictionaryGroup.PROP_SKU_STYLE, code)
              .getLabel());
    }
    propSkuFieldsDTO.setStyleLabels(styleLabels);
    return propSkuFieldsDTO;
  }

  /** 从写入校验结果固化名称，不套展示占位规则或覆盖历史审核快照。 */
  private void labels(
      PropSkuFieldsDTO propSkuFieldsDTO, Map<DictionaryOptionKey, OptionVO> optionVOMap) {
    propSkuFieldsDTO.setTypeLabel(
        optionVOMap
            .get(
                new DictionaryOptionKey(
                    DictionaryGroup.PROP_SKU_TYPE, propSkuFieldsDTO.getTypeCode()))
            .getLabel());
    propSkuFieldsDTO.setFireResistantLabel(
        optionVOMap
            .get(
                new DictionaryOptionKey(
                    DictionaryGroup.PROP_SKU_FIRE_RESISTANT,
                    propSkuFieldsDTO.getFireResistantCode()))
            .getLabel());
    List<String> styleLabels = new ArrayList<>();
    for (String code : propSkuFieldsDTO.getStyleCodes())
      styleLabels.add(
          optionVOMap
              .get(new DictionaryOptionKey(DictionaryGroup.PROP_SKU_STYLE, code))
              .getLabel());
    propSkuFieldsDTO.setStyleLabels(styleLabels);
  }

  /** 保留可空文本的语义，并校验非空文本长度。 */
  private String snapshotText(String value, int limit) {
    return value == null ? null : text(value, limit);
  }

  /** 按既定精度转换尺寸或重量，保持空值含义。 */
  private String dimension(String raw) {
    BigDecimal n = new BigDecimal(text(raw, 128));
    if (n.signum() <= 0 || n.scale() != 2 || n.precision() > 10) throw ApiFailure.invalid();
    return n.toPlainString();
  }

  /** 校验输入、字典和材料引用后形成审核使用的字段快照。 */
  public PropSkuFieldsDTO build(String actor, SkuInputDTO skuInputDTO) {
    return build(actor, skuInputDTO, List.of(), List.of(), false);
  }

  /** 资料更新继承不可在本入口修改的原类型与风格，停用字典原值可保留。 */
  public PropSkuFieldsDTO buildChange(String actor, SkuInputDTO skuInputDTO, PropSkuDO propSkuDO) {
    return build(
        actor, skuInputDTO, List.of(propSkuDO.getTypeCode()), skuInputDTO.styleCodes(), true);
  }

  /** 共用字段检查；只有原值继承和图片引用错误码随正式入口区别。 */
  private PropSkuFieldsDTO build(
      String actor,
      SkuInputDTO skuInputDTO,
      List<String> retainedTypes,
      List<String> retainedStyles,
      boolean change) {
    PropSkuFieldsDTO propSkuFieldsDTO = new PropSkuFieldsDTO();
    propSkuFieldsDTO.setPropName(snapshotText(skuInputDTO.propName(), 128));
    propSkuFieldsDTO.setTypeCode(snapshotText(skuInputDTO.typeCode(), 128));
    propSkuFieldsDTO.setSizeDesc(snapshotText(skuInputDTO.sizeDesc(), 128));
    propSkuFieldsDTO.setLengthCm(dimension(skuInputDTO.lengthCm()));
    propSkuFieldsDTO.setWidthCm(dimension(skuInputDTO.widthCm()));
    propSkuFieldsDTO.setHeightCm(dimension(skuInputDTO.heightCm()));
    propSkuFieldsDTO.setMaterialDesc(snapshotText(skuInputDTO.materialDesc(), 128));
    propSkuFieldsDTO.setFireResistantCode(snapshotText(skuInputDTO.fireResistantCode(), 128));
    propSkuFieldsDTO.setWeightKg(dimension(skuInputDTO.weightKg()));
    if (skuInputDTO.packagingFactorBps() == null
        || skuInputDTO.packagingFactorBps() < 10000
        || skuInputDTO.packagingFactorBps() > 1000000) throw ApiFailure.invalid();
    propSkuFieldsDTO.setPackagingFactorBps(skuInputDTO.packagingFactorBps());
    propSkuFieldsDTO.setTransportSuggestion(snapshotText(skuInputDTO.transportSuggestion(), 255));
    propSkuFieldsDTO.setDailyRentPriceFen(skuInputDTO.dailyRentPriceFen());
    propSkuFieldsDTO.setDepositAmountFen(skuInputDTO.depositAmountFen());
    propSkuFieldsDTO.setRemark(snapshotText(skuInputDTO.remark(), 255));
    if (skuInputDTO.fireResistantCode() == null) throw ApiFailure.invalid();
    Map<DictionaryOptionKey, OptionVO> optionVOMap =
        propCatalogService.validateSelection(
            skuInputDTO.typeCode(),
            skuInputDTO.fireResistantCode(),
            skuInputDTO.styleCodes(),
            retainedTypes,
            retainedStyles);
    propSkuFieldsDTO.setStyleCodes(skuInputDTO.styleCodes());
    List<String> keys = new ArrayList<>();
    for (com.mujian.backend.prop.dto.ImageInputDTO imageInputDTO : skuInputDTO.images()) {
      try {
        keys.add(propMaterialService.resolve(actor, imageInputDTO, "prop_image"));
      } catch (ApiFailure apiFailure) {
        if (change
            && Set.of(
                    D2Codes.CODE_MATERIAL_REFERENCE_INVALID,
                    D2Codes.CODE_MATERIAL_REFERENCE_EXPIRED,
                    D2Codes.CODE_RESOURCE_NOT_FOUND,
                    D2Codes.CODE_REQUEST_INVALID)
                .contains(apiFailure.reason))
          throw conflict(com.mujian.backend.common.enums.PropReasonCode.PROP_IMAGE_INVALID.name());
        throw apiFailure;
      }
    }
    if (new HashSet<>(keys).size() != keys.size()) throw ApiFailure.invalid();
    propSkuFieldsDTO.setImageKeys(keys);
    labels(propSkuFieldsDTO, optionVOMap);
    return propSkuFieldsDTO;
  }

  /** 固化道具资料、版本和申请上下文供审核与历史展示。 */
  public PropSkuSnapshotDTO snapshot(
      PropSkuDO propSkuDO, PropSkuFieldsDTO propSkuFieldsDTO, Integer initial, String previous) {
    return new PropSkuSnapshotDTO(
        2,
        propSkuDO.getId(),
        propSkuDO.getSkuCode(),
        propSkuDO.getFactoryId(),
        propSkuDO.getAssetType(),
        propSkuDO.getSkuStatus(),
        propSkuDO.getVersion(),
        propSkuFieldsDTO,
        initial,
        previous);
  }

  /** 纯字段映射，不查询数据库。 */
  public PropSkuDO skuFields(PropSkuFieldsDTO propSkuFieldsDTO) {
    return new PropSkuDO()
        .propName(propSkuFieldsDTO.getPropName())
        .typeCode(propSkuFieldsDTO.getTypeCode())
        .sizeDesc(propSkuFieldsDTO.getSizeDesc())
        .lengthCm(new BigDecimal(propSkuFieldsDTO.getLengthCm()))
        .widthCm(new BigDecimal(propSkuFieldsDTO.getWidthCm()))
        .heightCm(new BigDecimal(propSkuFieldsDTO.getHeightCm()))
        .materialDesc(propSkuFieldsDTO.getMaterialDesc())
        .fireResistantCode(propSkuFieldsDTO.getFireResistantCode())
        .weightKg(new BigDecimal(propSkuFieldsDTO.getWeightKg()))
        .packagingFactorBps(propSkuFieldsDTO.getPackagingFactorBps())
        .transportSuggestion(propSkuFieldsDTO.getTransportSuggestion())
        .dailyRentPriceFen(propSkuFieldsDTO.getDailyRentPriceFen())
        .depositAmountFen(propSkuFieldsDTO.getDepositAmountFen())
        .remark(propSkuFieldsDTO.getRemark())
        .imageUrl(propSkuFieldsDTO.getImageKeys().get(0));
  }

  /** 将已批准道具字段与图片写入当前生效资料。 */
  public void writeFields(String sku, PropSkuFieldsDTO propSkuFieldsDTO) {
    propSkuMapper.updatePropSkuFields(skuFields(propSkuFieldsDTO).id(sku));
    propImageMapper.deletePropImagesBySkuId(sku);
    int i = 0;
    for (String key : propSkuFieldsDTO.getImageKeys())
      propImageMapper.insertPropImage(
          new PropImageDO()
              .id(Ids.next())
              .skuId(sku)
              .imageUrl(key)
              .sortOrder(i++)
              .createdAt(now(clock)));
    writeStyles(sku, propSkuFieldsDTO.getStyleCodes());
  }

  /** 按提交顺序替换道具风格关联。 */
  public void writeStyles(String sku, List<String> styles) {
    propSkuStyleMapper.deletePropSkuStylesBySkuId(sku);
    int i = 0;
    for (String code : styles)
      propSkuStyleMapper.insertPropSkuStyle(
          new PropSkuStyleDO()
              .id(Ids.next())
              .skuId(sku)
              .styleCode(code)
              .sortOrder(i++)
              .createdAt(now(clock)));
  }

  /** 将已保存的道具字段及授权材料引用转换为页面快照。 */
  public SkuReadVO read(
      PropSkuFieldsDTO propSkuFieldsDTO,
      String source,
      String id,
      String section,
      boolean preview) {
    SkuReadVO skuReadVO = new SkuReadVO();
    skuReadVO.setPropName(propSkuFieldsDTO.getPropName());
    skuReadVO.setTypeCode(propSkuFieldsDTO.getTypeCode());
    skuReadVO.setSizeDesc(propSkuFieldsDTO.getSizeDesc());
    skuReadVO.setLengthCm(propSkuFieldsDTO.getLengthCm());
    skuReadVO.setWidthCm(propSkuFieldsDTO.getWidthCm());
    skuReadVO.setHeightCm(propSkuFieldsDTO.getHeightCm());
    skuReadVO.setMaterialDesc(propSkuFieldsDTO.getMaterialDesc());
    skuReadVO.setFireResistantCode(propSkuFieldsDTO.getFireResistantCode());
    skuReadVO.setWeightKg(propSkuFieldsDTO.getWeightKg());
    skuReadVO.setPackagingFactorBps(propSkuFieldsDTO.getPackagingFactorBps());
    skuReadVO.setTransportSuggestion(propSkuFieldsDTO.getTransportSuggestion());
    skuReadVO.setDailyRentPriceFen(propSkuFieldsDTO.getDailyRentPriceFen());
    skuReadVO.setDepositAmountFen(propSkuFieldsDTO.getDepositAmountFen());
    skuReadVO.setRemark(propSkuFieldsDTO.getRemark());
    if (propSkuFieldsDTO.getStyleCodes() == null
        || propSkuFieldsDTO.getStyleLabels() == null
        || propSkuFieldsDTO.getStyleCodes().size() != propSkuFieldsDTO.getStyleLabels().size()
        || propSkuFieldsDTO.getTypeLabel() == null
        || propSkuFieldsDTO.getFireResistantLabel() == null) throw PropAccess.inconsistent();
    skuReadVO.setStyleCodes(propSkuFieldsDTO.getStyleCodes());
    skuReadVO.setStyleLabels(propSkuFieldsDTO.getStyleLabels());
    skuReadVO.setTypeLabel(propSkuFieldsDTO.getTypeLabel());
    skuReadVO.setFireResistantLabel(propSkuFieldsDTO.getFireResistantLabel());
    skuReadVO.setImages(
        propMaterialService.images(propSkuFieldsDTO.getImageKeys(), source, id, section, preview));
    return skuReadVO;
  }

  /** 读取已保存审核快照中的道具字段。 */
  public PropSkuFieldsDTO fields(String snapshot) {
    return propJson.objectMapper.readValue(snapshot, PropSkuSnapshotDTO.class).fields();
  }

  /** 核对审核引用的对象材料仍然存在。 */
  public void verifyMaterialApproval(PropSkuFieldsDTO propSkuFieldsDTO) {
    propMaterialService.verifyStored(propSkuFieldsDTO.getImageKeys());
  }

  /** 核对审核材料和字典仍满足批准条件。 */
  public void verifyApproval(PropSkuFieldsDTO propSkuFieldsDTO) {
    if (propSkuFieldsDTO.getFireResistantCode() == null
        || propSkuFieldsDTO.getPackagingFactorBps() == null
        || propSkuFieldsDTO.getPackagingFactorBps() < 10000
        || propSkuFieldsDTO.getPackagingFactorBps() > 1000000) throw ApiFailure.invalid();
    propCatalogService.validateSelection(
        propSkuFieldsDTO.getTypeCode(),
        propSkuFieldsDTO.getFireResistantCode(),
        propSkuFieldsDTO.getStyleCodes(),
        List.of(),
        List.of());
  }
}
