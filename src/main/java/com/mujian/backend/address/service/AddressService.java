package com.mujian.backend.address.service;

import com.mujian.backend.address.dto.AddressDtos.*;
import com.mujian.backend.address.vo.AddressViews.*;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.vo.PlanningViews.*;

public interface AddressService {
  /** 本人有效地址分页。 */
  PageResult<Address> list(PlanningQuery planningQuery);

  /** 本人有效地址详情。 */
  Address detail(String actor, String id);

  /** 新建地址。 */
  Receipt create(PlanningCommand planningCommand, CreateAddress createAddress);

  /** 更新地址所有可变字段。 */
  Receipt update(PlanningCommand planningCommand, String id, UpdateAddress updateAddress);

  /** 设置默认或软删地址。 */
  Receipt changeState(
      PlanningCommand planningCommand, String id, ExpectedVersion expectedVersion, boolean delete);
}
