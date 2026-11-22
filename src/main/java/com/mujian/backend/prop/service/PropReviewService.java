package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.dto.ReviewDecisionDTO;
import com.mujian.backend.prop.projection.PropReviewProjection;
import com.mujian.backend.prop.vo.ReviewResultVO;
import java.util.*;

/** PropReviewService业务入口；实现保留领域协作与事务边界。 */
public interface PropReviewService {
  /** 校验权限后恢复指定请求已产生的业务结果。 */
  ReviewResultVO recover(String actor, String key);

  /** 核对当前业务状态是否仍满足批准条件。 */
  void approvable(String type, PropReviewProjection propReviewProjection, PropSkuDO propSkuDO);

  /** 按原锁序处理审核决定，并统一提交业务变更和审计。 */
  ReviewResultVO decide(
      String actor,
      String type,
      String auditId,
      String key,
      boolean approve,
      ReviewDecisionDTO reviewDecisionDTO,
      String trace);
}
