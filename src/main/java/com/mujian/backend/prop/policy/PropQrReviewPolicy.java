package com.mujian.backend.prop.policy;

import static com.mujian.backend.common.support.D2Values.conflict;

import com.mujian.backend.common.enums.PropQrTargetType;
import com.mujian.backend.common.enums.PropReasonCode;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.projection.PropReviewProjection;
import java.util.Objects;

/** 风险码审核规则供只读能力与锁内批准共同使用。 */
public final class PropQrReviewPolicy {
  /** 仅提供无I/O的规则检查。 */
  private PropQrReviewPolicy() {}

  /** 核对二维码与申请归属，保留原异常优先级与状态判断。 */
  public static void assertApprovable(
      PropReviewProjection propReviewProjection, PropSkuDO propSkuDO, PropQrCodeDO propQrCodeDO) {
    if (propQrCodeDO == null
        || !Objects.equals(propQrCodeDO.getSkuId(), propSkuDO.getId())
        || !Objects.equals(propReviewProjection.getFactoryId(), propSkuDO.getFactoryId())
        || !(PropQrTargetType.SKU.name().equals(propQrCodeDO.getTargetType())
            ? propReviewProjection.getSnId() == null
                && propSkuDO.getId().equals(propQrCodeDO.getTargetId())
            : PropQrTargetType.SN.name().equals(propQrCodeDO.getTargetType())
                && Objects.equals(propReviewProjection.getSnId(), propQrCodeDO.getTargetId())))
      throw PropAccess.inconsistent();
    if (!"bound".equals(propQrCodeDO.getStatus())) throw conflict(PropReasonCode.QR_REVOKED.name());
  }
}
