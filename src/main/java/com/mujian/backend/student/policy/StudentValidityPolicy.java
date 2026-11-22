package com.mujian.backend.student.policy;

import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.common.api.ApiFailure;
import java.time.LocalDateTime;

public final class StudentValidityPolicy {
  /** 按管理员选择的一至五年计算认证有效期。 */
  public static LocalDateTime expiry(LocalDateTime reviewedAt, int years) {
    if (years < 1 || years > 5) throw ApiFailure.invalid();
    return reviewedAt.plusYears(years);
  }

  /** 在读取时体现已批准认证的实际到期状态。 */
  public static String status(String persisted, LocalDateTime expireAt, LocalDateTime now) {
    return CODE_APPROVED.equals(persisted) && expireAt != null && !now.isBefore(expireAt)
        ? CODE_EXPIRED
        : persisted;
  }
}
