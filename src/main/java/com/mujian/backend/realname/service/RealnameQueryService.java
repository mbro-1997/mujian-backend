package com.mujian.backend.realname.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.support.*;
import com.mujian.backend.material.service.*;
import com.mujian.backend.realname.domain.RealnameVerificationDO;
import com.mujian.backend.realname.vo.*;
import com.mujian.backend.verification.dto.VerificationListQueryDTO;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

public interface RealnameQueryService {
  /** 转换申请展示信息。 */
  RealnameApplicationVO application(RealnameVerificationDO realnameVerificationDO);

  /** 读取当前申请。 */
  RealnameVerificationDO currentRow(String user);

  /** 查询当前申请展示结果。 */
  RealnameApplicationVO currentApplication(String user);

  /** 读取当前认证状态。 */
  String currentStatus(String user);

  /** 返回认证进度及下一步操作。 */
  RealnameCurrentVO current(String user);

  /** 按原请求编号恢复申请。 */
  RealnameApplicationVO recover(String user, String key);

  /** 读取管理审核详情。 */
  RealnameDetailVO detail(String actor, String id);

  /** 查询审核列表。 */
  PageResult<RealnameListItemVO> list(
      String actor, VerificationListQueryDTO verificationListQueryDTO);
}
