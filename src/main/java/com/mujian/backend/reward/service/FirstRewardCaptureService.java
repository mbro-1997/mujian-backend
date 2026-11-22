package com.mujian.backend.reward.service;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.enums.FirstRewardEvent;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.reward.mapper.FirstRewardMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class FirstRewardCaptureService {
  private final FirstRewardMapper firstRewardMapper;

  /** 共用三个来源的首次事实表。 */
  public FirstRewardCaptureService(FirstRewardMapper firstRewardMapper) {
    this.firstRewardMapper = firstRewardMapper;
  }

  /** 跟随已核验的来源事务保存首次资格，发券在独立事务执行。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void capture(
      FirstRewardEvent event,
      String user,
      String source,
      boolean eligibleRole,
      boolean active,
      LocalDateTime now) {
    String rule = firstRewardMapper.selectActiveRule(event);
    String reason = null;
    if (!eligibleRole) reason = "ROLE_NOT_ELIGIBLE";
    else if (!active) reason = D2Codes.CODE_ACCOUNT_RESTRICTED;
    else if (rule == null) reason = "NO_ACTIVE_RULE";
    firstRewardMapper.insertFirstFact(Ids.next(), user, event, source, rule, reason, now);
  }
}
