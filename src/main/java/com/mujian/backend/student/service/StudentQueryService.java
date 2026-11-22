package com.mujian.backend.student.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.support.*;
import com.mujian.backend.material.service.*;
import com.mujian.backend.student.domain.StudentVerificationDO;
import com.mujian.backend.student.vo.*;
import com.mujian.backend.verification.dto.VerificationListQueryDTO;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

public interface StudentQueryService {
  /** 转换申请展示信息。 */
  StudentApplicationVO application(StudentVerificationDO studentVerificationDO);

  /** 读取当前申请。 */
  StudentVerificationDO currentRow(String user);

  /** 查询当前申请展示结果。 */
  StudentApplicationVO currentApplication(String user);

  /** 返回认证进度及下一步操作。 */
  StudentCurrentVO current(String user);

  /** 按原请求编号恢复申请。 */
  StudentApplicationVO recover(String user, String key);

  /** 读取管理审核详情。 */
  StudentDetailVO detail(String actor, String id);

  /** 查询审核列表。 */
  PageResult<StudentListItemVO> list(
      String actor, VerificationListQueryDTO verificationListQueryDTO);
}
