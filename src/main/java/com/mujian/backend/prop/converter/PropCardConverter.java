package com.mujian.backend.prop.converter;

import com.mujian.backend.prop.projection.PropBrowseProjection;
import com.mujian.backend.prop.vo.FactorySummaryVO;
import com.mujian.backend.prop.vo.OptionVO;
import com.mujian.backend.prop.vo.PropCardVO;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import java.util.List;

/** 纯转换；所有字典、关联和签名都由调用方准备，不在循环里查数据库。 */
public final class PropCardConverter {
  /** 限制工具类实例化，统一通过静态方法使用。 */
  private PropCardConverter() {}

  /** 将已准备的道具、字典和图片数据转换为展示卡片。 */
  public static PropCardVO card(
      PropBrowseProjection propBrowseProjection,
      OptionVO optionVO,
      List<OptionVO> optionVOList,
      PublicImageViewVO publicImageViewVO) {
    return new PropCardVO()
        .skuId(propBrowseProjection.skuId())
        .propName(propBrowseProjection.propName())
        .assetType(
            com.mujian.backend.common.enums.PropAssetType.valueOf(propBrowseProjection.assetType())
                .name())
        .cover(publicImageViewVO)
        .type(optionVO)
        .styles(List.copyOf(optionVOList))
        .dailyRentPriceFen(propBrowseProjection.dailyRentPriceFen())
        .depositAmountFen(propBrowseProjection.depositAmountFen())
        .factory(
            new FactorySummaryVO()
                .id(propBrowseProjection.factoryId())
                .name(propBrowseProjection.factoryName()));
  }
}
