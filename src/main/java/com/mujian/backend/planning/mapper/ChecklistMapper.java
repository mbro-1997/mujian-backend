package com.mujian.backend.planning.mapper;

import com.mujian.backend.planning.domain.*;
import com.mujian.backend.planning.dto.PlanningQuery;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ChecklistMapper {

  /** 查询本人清单并按需锁根。 */
  RentalChecklistDO selectOwnedChecklistById(
      @Param("actor") String actor, @Param("id") String id, @Param("lock") boolean lock);

  /** 删除重放及历史支付根定位保留逻辑删除行，仍限定本人归属。 */
  RentalChecklistDO selectOwnedChecklistIncludingDeletedById(
      @Param("actor") String actor, @Param("id") String id, @Param("lock") boolean lock);

  /** 原子删除可整理清单，支付在途、转单及版本变化均不得越过条件更新。 */
  int markOwnedChecklistDeleted(
      @Param("actor") String actor,
      @Param("id") String id,
      @Param("expectedVersion") int expectedVersion,
      @Param("updatedVersion") int updatedVersion,
      @Param("now") java.time.LocalDateTime now);

  /** 读取未转单根，按ID加锁以保证切换锁序。 */
  List<RentalChecklistDO> selectActiveChecklistsByActor(
      @Param("actor") String actor, @Param("lock") boolean lock);

  /** 统计本人筛选后的清单。 */
  long selectChecklistCountByQuery(@Param("query") PlanningQuery planningQuery);

  /** 按租期分页读取本人清单。 */
  List<RentalChecklistDO> selectChecklistPageByQuery(@Param("query") PlanningQuery planningQuery);

  /** 保存新的选品清单。 */
  void insertChecklist(@Param("row") RentalChecklistDO rentalChecklistDO);

  /** 在用户根锁内修改状态或版本，不改租期。 */
  void updateChecklist(@Param("row") RentalChecklistDO rentalChecklistDO);

  /** 批量读取清单成员。 */
  List<RentalChecklistItemDO> selectChecklistItemsByChecklistIds(@Param("ids") List<String> ids);

  /** 保存已验证的清单成员。 */
  void insertChecklistItem(@Param("row") RentalChecklistItemDO rentalChecklistItemDO);

  /** 在清单根锁内替换完整成员。 */
  void deleteChecklistItemsByChecklistId(@Param("id") String id);
}
