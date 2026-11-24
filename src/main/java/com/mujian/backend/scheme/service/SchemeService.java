package com.mujian.backend.scheme.service;

import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.vo.PlanningViews.*;
import com.mujian.backend.scheme.dto.SchemeDtos.*;
import com.mujian.backend.scheme.vo.SchemeViews.*;

public interface SchemeService {
  /** 读取本人方案分页。 */
  PageResult<SchemeSummary> list(PlanningQuery planningQuery);

  /** 读取本人完整方案。 */
  SchemeDetail detail(String actor, String id);

  /** 新建方案并分配槽位。 */
  Receipt create(PlanningCommand planningCommand, CreateScheme createScheme);

  /** 全量保存元信息和明细。 */
  Receipt update(PlanningCommand planningCommand, String id, UpdateScheme updateScheme);

  /** 归档不可逆。 */
  Receipt archive(PlanningCommand planningCommand, String id, ExpectedVersion expectedVersion);

  /** 删除方案，原键可在主记录删除后恢复成功回执。 */
  Receipt delete(PlanningCommand command, String id, ExpectedVersion input);

  /** 将SKU设置为最终数量。 */
  Receipt setItem(PlanningCommand planningCommand, String id, ItemSet itemSet);

  /** 删除一个本人方案成员。 */
  Receipt removeItem(
      PlanningCommand planningCommand, String id, String itemId, ExpectedVersion expectedVersion);

  /** 全量排序本人方案成员。 */
  Receipt reorder(PlanningCommand planningCommand, String id, ReorderScheme reorderScheme);

  /** 保存或关闭分享，保留独立内容版本。 */
  ShareIssued share(PlanningCommand planningCommand, String id, ShareSet shareSet, boolean disable);

  /** 匿名读取当前有效分享。 */
  SharedScheme shared(String token);
}
