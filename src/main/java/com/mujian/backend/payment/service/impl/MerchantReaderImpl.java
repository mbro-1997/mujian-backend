package com.mujian.backend.payment.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.service.RentalOrderReader;
import com.mujian.backend.payment.domain.MerchantStatusSnapshot;
import com.mujian.backend.payment.mapper.MerchantMapper;
import com.mujian.backend.payment.projection.MerchantSourceProjection;
import com.mujian.backend.payment.service.*;
import com.mujian.backend.payment.vo.MerchantAccountVO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 显示已有渠道事实，未开户工厂返回明确未申请且不创建账户。 */
@Service
public class MerchantReaderImpl implements MerchantReader {
  private final MerchantMapper merchantMapper;
  private final RentalOrderReader rentalOrderReader;
  private final VerificationAccess verificationAccess;
  private final ObjectMapper objectMapper;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public MerchantReaderImpl(
      MerchantMapper merchantMapper,
      RentalOrderReader rentalOrderReader,
      VerificationAccess verificationAccess,
      ObjectMapper objectMapper) {
    this.merchantMapper = merchantMapper;
    this.rentalOrderReader = rentalOrderReader;
    this.verificationAccess = verificationAccess;
    this.objectMapper = objectMapper;
  }

  /** 按当前工厂身份限定数据范围。 */
  public String factory(String actor) {
    return rentalOrderReader.supplierFactory(actor);
  }

  /** 核对本人或本工厂归属，合法历史不因新增交易限制而丢失。 */
  public MerchantAccountVO owned(String actor) {
    return view(merchantMapper.selectMerchantByFactory(factory(actor), false));
  }

  /** 按商户菜单权限读取脱敏账户状态。 */
  public MerchantAccountVO admin(String actor, String factoryId) {
    verificationAccess.admin(actor, "admin:trade:merchant:read");
    return view(merchantMapper.selectMerchantByFactory(factoryId, false));
  }

  /** 先按授权范围筛选统计，再读取当前页业务数据。 */
  public PageResult<MerchantAccountVO> page(
      String actor, String factoryId, String status, PageQuery pageQueryPage) {
    verificationAccess.admin(actor, "admin:trade:merchant:read");
    List<MerchantAccountVO> merchantAccountVOList = new ArrayList<>();
    for (MerchantSourceProjection merchantSourceProjection :
        merchantMapper.selectMerchantPage(factoryId, status, pageQueryPage))
      merchantAccountVOList.add(view(merchantSourceProjection));
    return PageResult.of(
        merchantAccountVOList, merchantMapper.countMerchants(factoryId, status), pageQueryPage);
  }

  /** 将可信商户事实转换为脱敏展示及获准办理动作。 */
  public MerchantAccountVO view(MerchantSourceProjection merchantSourceProjectionSource) {
    if (merchantSourceProjectionSource == null) throw missing();
    String actionUrl = null;
    if (MerchantAccountStatus.need_action
            .name()
            .equals(merchantSourceProjectionSource.accountStatus())
        && merchantSourceProjectionSource.channelStatusSnapshotJson() != null) {
      MerchantStatusSnapshot merchantStatusSnapshotSnapshot =
          objectMapper.readValue(
              merchantSourceProjectionSource.channelStatusSnapshotJson(),
              MerchantStatusSnapshot.class);
      actionUrl = merchantStatusSnapshotSnapshot.actionUrl();
    }
    String reason = null;
    if (MerchantAccountStatus.not_applied
        .name()
        .equals(merchantSourceProjectionSource.accountStatus())) reason = "MERCHANT_NOT_APPLIED";
    else if (MerchantAccountStatus.reviewing
        .name()
        .equals(merchantSourceProjectionSource.accountStatus())) reason = "MERCHANT_REVIEWING";
    else if (MerchantAccountStatus.need_action
        .name()
        .equals(merchantSourceProjectionSource.accountStatus()))
      reason = "MERCHANT_ACTION_REQUIRED";
    else if (D2Codes.CODE_REJECTED.equals(merchantSourceProjectionSource.accountStatus()))
      reason = "MERCHANT_REJECTED";
    else if (MerchantAccountStatus.frozen
            .name()
            .equals(merchantSourceProjectionSource.accountStatus())
        || TradingState.closed.name().equals(merchantSourceProjectionSource.accountStatus())
        || !D2Codes.CODE_ACTIVE.equals(merchantSourceProjectionSource.profitSharingStatus()))
      reason = "MERCHANT_RESTRICTED";
    String next =
        reason == null
            ? "ready"
            : actionUrl != null
                ? "open_channel"
                : MerchantAccountStatus.reviewing
                        .name()
                        .equals(merchantSourceProjectionSource.accountStatus())
                    ? TradingNextAction.wait.name()
                    : "contact_platform";
    String masked =
        merchantSourceProjectionSource.subMchid() == null
            ? null
            : "****"
                + merchantSourceProjectionSource
                    .subMchid()
                    .substring(Math.max(0, merchantSourceProjectionSource.subMchid().length() - 4));
    return new MerchantAccountVO(
        merchantSourceProjectionSource.id(),
        merchantSourceProjectionSource.factoryId(),
        merchantSourceProjectionSource.version(),
        merchantSourceProjectionSource.accountStatus(),
        merchantSourceProjectionSource.profitSharingStatus(),
        merchantSourceProjectionSource.subjectName(),
        masked,
        time(merchantSourceProjectionSource.lastSyncedAt()),
        false,
        next,
        actionUrl,
        reason);
  }
}
