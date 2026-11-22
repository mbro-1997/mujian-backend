package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.payment.projection.MerchantSourceProjection;
import com.mujian.backend.payment.vo.MerchantAccountVO;

/** 工厂收款商户的归属和状态查询。 */
public interface MerchantReader {
  /** 核实调用者的工厂身份并取得工厂标识。 */
  String factory(String actor);

  /** 读取当前工厂的收款商户状态。 */
  MerchantAccountVO owned(String actor);

  /** 按后台权限查询指定工厂的商户资料。 */
  MerchantAccountVO admin(String actor, String factoryId);

  /** 按筛选条件分页列出工厂收款商户。 */
  PageResult<MerchantAccountVO> page(
      String actor, String factoryId, String status, PageQuery pageQueryPage);

  /** 将商户来源记录映射为对外可见的账户状态。 */
  MerchantAccountVO view(MerchantSourceProjection merchantSourceProjectionSource);
}
