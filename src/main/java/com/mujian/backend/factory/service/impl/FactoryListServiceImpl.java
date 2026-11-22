package com.mujian.backend.factory.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.domain.FactoryChangeSnapshot;
import com.mujian.backend.factory.dto.*;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.QueueItem;
import com.mujian.backend.factory.mapper.FactoryInviteCodeMapper;
import com.mujian.backend.factory.mapper.FactoryProfileMapper;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.projection.*;
import com.mujian.backend.factory.service.*;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FactoryListServiceImpl implements FactoryListService {
  private final FactoryInviteCodeMapper factoryInviteCodeMapper;
  private final FactoryReviewMapper factoryReviewMapper;
  private final FactoryProfileMapper factoryProfileMapper;
  private final FactoryAccess factoryAccess;
  private final FactorySnapshotData factorySnapshotData;
  private final FactoryReadService factoryReadService;
  private final FactoryInvitationService factoryInvitationService;
  private final Clock clock;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryListServiceImpl(
      FactoryInviteCodeMapper factoryInviteCodeMapper,
      FactoryReviewMapper factoryReviewMapper,
      FactoryProfileMapper factoryProfileMapper,
      FactoryAccess factoryAccess,
      FactorySnapshotData factorySnapshotData,
      FactoryReadService factoryReadService,
      FactoryInvitationService factoryInvitationService,
      Clock clock) {
    this.factoryInviteCodeMapper = factoryInviteCodeMapper;
    this.factoryReviewMapper = factoryReviewMapper;
    this.factoryProfileMapper = factoryProfileMapper;
    this.factoryAccess = factoryAccess;
    this.factorySnapshotData = factorySnapshotData;
    this.factoryReadService = factoryReadService;
    this.factoryInvitationService = factoryInvitationService;
    this.clock = clock;
  }

  /** 解析分页及时间条件，保持原默认值、错误边界和时区。 */
  private FactoryPageQueryDTO parse(FactoryListQueryDTO factoryListQueryDTO, boolean reviewQueue) {
    LocalDateTime from =
        factoryListQueryDTO.getFrom() != null
            ? OffsetDateTime.parse(factoryListQueryDTO.getFrom())
                .atZoneSameInstant(ZONE)
                .toLocalDateTime()
            : null;
    LocalDateTime to =
        factoryListQueryDTO.getTo() != null
            ? OffsetDateTime.parse(factoryListQueryDTO.getTo())
                .atZoneSameInstant(ZONE)
                .toLocalDateTime()
            : null;
    if (from != null && to != null && !from.isBefore(to)) throw ApiFailure.invalid();
    String keyword = factoryListQueryDTO.getKeyword();
    if (keyword != null) {
      keyword = keyword.trim();
      if (keyword.isEmpty()) throw ApiFailure.invalid();
    }
    int page =
        com.mujian.backend.common.api.PageQuery.parse(
            factoryListQueryDTO.getPage(), 1, Integer.MAX_VALUE);
    int pageSize =
        com.mujian.backend.common.api.PageQuery.parse(factoryListQueryDTO.getPageSize(), 20, 100);
    return new FactoryPageQueryDTO(
        page,
        pageSize,
        (long) (page - 1) * pageSize,
        keyword,
        reviewQueue && factoryListQueryDTO.getStatus() == null
            ? CODE_PENDING
            : factoryListQueryDTO.getStatus(),
        from,
        to,
        now(clock));
  }

  /** 按筛选条件分页返回邀请码列表。 */
  public InvitationPage invites(String actor, FactoryListQueryDTO factoryListQueryDTO) {
    java.util.List<String> permissions = factoryAccess.readPermissions(actor, "factory-invite");
    boolean manage = permissions.contains("admin:" + "factory-invite" + ":manage");
    FactoryPageQueryDTO factoryPageQueryDTO = parse(factoryListQueryDTO, false);
    long total = factoryInviteCodeMapper.selectFactoryInvitationCountByQuery(factoryPageQueryDTO);
    List<FactoryInviteProjection> rows =
        factoryPageQueryDTO.offset() >= total
            ? List.of()
            : factoryInviteCodeMapper.selectFactoryInvitationPageByQuery(factoryPageQueryDTO);
    List<Invitation> invitationList = new ArrayList<>();
    for (FactoryInviteProjection factoryInviteProjection : rows) {
      invitationList.add(
          factoryInvitationService.invitation(
              factoryInviteProjection, factoryInviteProjection.getSubmitted() == 1, manage));
    }
    return new InvitationPage(
        invitationList,
        factoryPageQueryDTO.page(),
        factoryPageQueryDTO.limit(),
        Long.toString(total));
  }

  /** 按审核来源和筛选条件分页返回待审申请。 */
  public QueuePage queue(
      String actor, FactorySource factorySource, FactoryListQueryDTO factoryListQueryDTO) {
    factoryAccess.readPermissions(actor, factorySource.permission);
    FactoryPageQueryDTO factoryPageQueryDTO = parse(factoryListQueryDTO, true);
    long count =
        factoryReviewMapper.selectFactoryReviewCountByQuery(factoryPageQueryDTO, factorySource);
    List<FactorySourceProjection> rows =
        factoryPageQueryDTO.offset() >= count
            ? List.of()
            : factoryReviewMapper.selectFactoryReviewPageByQuery(
                factoryPageQueryDTO, factorySource);
    List<QueueItem> items = new ArrayList<>();
    for (FactorySourceProjection factorySourceProjection : rows) {
      FactoryChangeSnapshot factoryChangeSnapshot =
          factorySource == FactorySource.CHANGE
              ? factorySnapshotData.change(factorySourceProjection)
              : null;
      // 入驻列表只展示名称和脱敏信用代码，不解析照片、案例等详情JSON。
      String factoryName =
          factoryChangeSnapshot == null
              ? factorySourceProjection.getFactoryName()
              : factoryChangeSnapshot.proposed().factory_name();
      String credit =
          factoryChangeSnapshot == null
              ? factorySourceProjection.getUnifiedSocialCreditCode()
              : factoryChangeSnapshot.proposed().unified_social_credit_code();
      items.add(
          new QueueItem(
              factorySourceProjection.getId(),
              factorySourceProjection.getApplicationNo(),
              factorySource == FactorySource.CHANGE
                  ? factorySourceProjection.getFactoryId()
                  : factorySourceProjection.getCreatedFactoryId(),
              factoryName,
              factorySourceProjection.getApplicantUserId(),
              factorySourceProjection.getApplicantNickname(),
              credit.substring(0, 4) + "**********" + credit.substring(14),
              time(factorySourceProjection.getSubmittedAt()),
              factorySourceProjection.getAuditStatus(),
              factoryChangeSnapshot == null
                  ? List.of()
                  : factorySnapshotData.changed(
                      factoryChangeSnapshot.before(), factoryChangeSnapshot.proposed())));
    }
    return new QueuePage(
        items, factoryPageQueryDTO.page(), factoryPageQueryDTO.limit(), Long.toString(count));
  }

  /** 按权限和筛选条件分页返回工厂目录。 */
  public FactoryPage factories(String actor, FactoryListQueryDTO factoryListQueryDTO) {
    factoryAccess.readPermissions(actor, "factory");
    FactoryPageQueryDTO factoryPageQueryDTO = parse(factoryListQueryDTO, false);
    long count = factoryProfileMapper.selectFactoryProfileCountByQuery(factoryPageQueryDTO);
    List<FactorySourceProjection> rows =
        factoryPageQueryDTO.offset() >= count
            ? List.of()
            : factoryProfileMapper.selectFactoryProfilePageByQuery(factoryPageQueryDTO);
    List<AdminFactory> adminFactoryList = new ArrayList<>();
    for (FactorySourceProjection factorySourceProjection : rows) {
      adminFactoryList.add(factoryReadService.adminProfile(factorySourceProjection));
    }
    return new FactoryPage(
        adminFactoryList,
        factoryPageQueryDTO.page(),
        factoryPageQueryDTO.limit(),
        Long.toString(count));
  }
}
