package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.*;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.projection.*;
import java.time.*;
import java.util.*;

/** 工厂业务契约，复用现有流程及响应。 */
public interface FactoryListService {

  /** 查询邀请码分页及操作能力。 */
  InvitationPage invites(String actor, FactoryListQueryDTO factoryListQueryDTO);

  /** 查询工厂审核队列。 */
  QueuePage queue(
      String actor, FactorySource factorySource, FactoryListQueryDTO factoryListQueryDTO);

  /** 查询工厂资料分页。 */
  FactoryPage factories(String actor, FactoryListQueryDTO factoryListQueryDTO);
}
