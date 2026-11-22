package com.mujian.backend.payment.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.payment.dto.MerchantWrites;
import com.mujian.backend.payment.projection.MerchantSourceProjection;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MerchantMapper {
  /** 按工厂根读取原渠道账户，按需锁定版本。 */
  MerchantSourceProjection selectMerchantByFactory(
      @Param("factory") String factory, @Param("lock") boolean lock);

  /** 按真实商户状态筛选分页，未进件工厂不伪造成已开通。 */
  List<MerchantSourceProjection> selectMerchantPage(
      @Param("factory") String factory,
      @Param("status") String status,
      @Param("page") PageQuery pageQueryPage);

  /** 使用与商户列表相同条件统计。 */
  long countMerchants(@Param("factory") String factory, @Param("status") String status);

  /** 原版本条件更新可信渠道同步事实，保留分账准入边界。 */
  int updateMerchantStatus(@Param("sync") MerchantWrites.Sync sync);
}
