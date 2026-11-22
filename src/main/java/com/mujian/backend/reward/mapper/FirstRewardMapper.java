package com.mujian.backend.reward.mapper;

import com.mujian.backend.common.enums.FirstRewardEvent;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FirstRewardMapper {
  /** 捕获当时启用的版本，与规则换版串行。 */
  String selectActiveRule(@Param("event") FirstRewardEvent event);

  /** 仅首次写入，重试不能覆盖最早来源和版本。 */
  void insertFirstFact(
      @Param("id") String id,
      @Param("user") String user,
      @Param("event") FirstRewardEvent event,
      @Param("source") String source,
      @Param("rule") String rule,
      @Param("reason") String reason,
      @Param("now") LocalDateTime now);
}
