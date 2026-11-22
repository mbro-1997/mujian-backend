package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.prop.dto.QrExceptionQueryDTO;
import com.mujian.backend.prop.dto.ReviewQueryDTO;
import com.mujian.backend.prop.vo.QrExceptionPageVO;
import com.mujian.backend.prop.vo.QrExceptionVO;
import com.mujian.backend.prop.vo.ReviewBaseVO;
import com.mujian.backend.prop.vo.ReviewPageVO;
import java.util.*;
import org.springframework.transaction.annotation.*;

/** PropReviewQueryService业务入口；实现保留领域协作与事务边界。 */
public interface PropReviewQueryService {
  /** 校验申请访问范围并组装审核快照、当前状态和可执行操作。 */
  ReviewBaseVO detail(String actor, String type, String id, boolean admin);

  /** 按审核条件分页查询申请，并返回各类待审数量。 */
  ReviewPageVO list(String actor, ReviewQueryDTO reviewQueryDTO, boolean admin);

  /** 校验管理权限后读取二维码异常处理详情。 */
  QrExceptionVO exception(String actor, String id);

  /** 查询二维码异常列表及状态分类数量。 */
  QrExceptionPageVO exceptions(String actor, QrExceptionQueryDTO qrExceptionQueryDTO);
}
