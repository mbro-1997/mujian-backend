package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.dto.LabelConfirmDTO;
import com.mujian.backend.prop.dto.QrExceptionCreateDTO;
import com.mujian.backend.prop.dto.QrIssueDTO;
import com.mujian.backend.prop.vo.CommandResultVO;
import com.mujian.backend.prop.vo.QrVO;
import java.util.*;

/** PropQrService业务入口；实现保留领域协作与事务边界。 */
public interface PropQrService {
  /** 读取指定二维码记录，缺失时按资源不存在处理。 */
  PropQrCodeDO row(String id);

  /** 组装二维码资料、码图及当前可执行操作。 */
  QrVO dto(String actor, PropQrCodeDO propQrCodeDO, boolean admin);

  /** 核对查看权限后返回二维码详情。 */
  QrVO read(String actor, String id, boolean admin);

  /** 根据扫码内容校验绑定状态并返回可访问的业务二维码。 */
  QrVO resolve(String actor, String code);

  /** 校验权限和码图状态后重置可重试的生成任务。 */
  QrVO retry(String actor, String id, boolean admin);

  /** 校验二维码、资产和版本后确认贴标并记录状态变更。 */
  CommandResultVO label(String actor, String snId, String key, LabelConfirmDTO labelConfirmDTO);

  /** 生成替换二维码并按原规则关联被替代记录。 */
  CommandResultVO issue(String actor, String key, QrIssueDTO qrIssueDTO, String trace);

  /** 校验权限与凭证后在事务中登记二维码异常申请。 */
  CommandResultVO report(String actor, String key, QrExceptionCreateDTO qrExceptionCreateDTO);
}
