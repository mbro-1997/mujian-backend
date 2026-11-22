package com.mujian.backend.planning.service;

import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.vo.PlanningViews.*;

public interface ChecklistService {
  /** 读取名额与唯一当前清单。 */
  Context context(String actor);

  /** 查询本人清单分页。 */
  PageResult<ChecklistSummary> list(PlanningQuery planningQuery);

  /** 按工厂组装当前可租投影。 */
  ChecklistDetail detail(String actor, String id);

  /** 手工或从方案完整复制。 */
  Receipt create(PlanningCommand planningCommand, CreateChecklist createChecklist);

  /** 原子切换当前清单。 */
  Receipt activate(PlanningCommand planningCommand, String id, ExpectedVersion expectedVersion);

  /** 继续选品设置最终总量。 */
  Receipt setItem(PlanningCommand planningCommand, String id, ItemSet itemSet);

  /** 更新既有成员。 */
  Receipt updateItem(
      PlanningCommand planningCommand,
      String id,
      String itemId,
      ChecklistItemUpdate checklistItemUpdate);

  /** 原子保存完整保留项。 */
  Receipt save(PlanningCommand planningCommand, String id, SaveChecklist saveChecklist);

  /** 删除一个已有项。 */
  Receipt removeItem(
      PlanningCommand planningCommand, String id, String itemId, ExpectedVersion expectedVersion);
}
