package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.infrastructure.storage.PrivateMaterialStore;
import com.mujian.backend.infrastructure.wechat.QrImage;
import com.mujian.backend.infrastructure.wechat.WechatGateway;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.mapper.PropQrCodeMapper;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Claim and publish are short CAS statements. Neither WeChat nor COS runs in a DB transaction. */
@Service
public class PropQrImages {
  private final PropQrCodeMapper propQrCodeMapper;
  private final WechatGateway wechatGateway;
  private final PrivateMaterialStore privateMaterialStore;
  private final Clock clock;
  private final String environment;
  private final boolean enabled;
  private static final int[] BACKOFF = {60, 300, 900, 3600};
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PropQrImages.class);

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropQrImages(
      PropQrCodeMapper propQrCodeMapper,
      WechatGateway wechatGateway,
      PrivateMaterialStore privateMaterialStore,
      Clock clock,
      @Value("${mj.wechat.qr-env-version:release}") String e,
      @Value("${mj.prop.qr-worker-enabled:true}") boolean on) {
    this.propQrCodeMapper = propQrCodeMapper;
    this.wechatGateway = wechatGateway;
    this.privateMaterialStore = privateMaterialStore;
    this.clock = clock;
    environment = e;
    enabled = on;
  }

  /** 取得生成租约后创建码图，并按原租约条件发布结果。 */
  public boolean generate(String id) {
    PropQrCodeDO propQrCodeDO = propQrCodeMapper.selectPropQrCode(id, false);
    if (propQrCodeDO == null
        || !propQrCodeDO.getStatus().equals("bound")
        || propQrCodeDO.getImageGenerationStatus().equals("ready")) return false;
    LocalDateTime now = now(clock);
    if (propQrCodeDO.getImageAttempts() >= 5) {
      expireExhausted();
      return false;
    }
    if (propQrCodeDO.getImageNextRetryAt() != null
        && propQrCodeDO.getImageNextRetryAt().isAfter(now)) return false;
    if (propQrCodeDO.getImageGenerationStatus().equals("generating")
        && propQrCodeDO.getImageLeaseUntil() != null
        && propQrCodeDO.getImageLeaseUntil().isAfter(now)) return false;

    if (propQrCodeMapper.updatePropQrImageLease(
            id, propQrCodeDO.getVersion(), now, now.plusSeconds(120))
        != 1) return false;
    int claimed = propQrCodeDO.getVersion() + 1, attempt = propQrCodeDO.getImageAttempts() + 1;
    try {
      byte[] byteArray =
          QrImage.png(
              wechatGateway.miniCode(
                  propQrCodeDO.getQrScene(), propQrCodeDO.getQrPage(), environment));
      String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(byteArray));
      String key = "dev/d2-c/qr-images/" + id + "/" + sha + ".png";
      privateMaterialStore.put(
          key,
          byteArray,
          "image/png",
          Map.of("purpose", "prop-qr-image", "qr-record-id", id, "sha256", sha));
      return propQrCodeMapper.updatePropQrImageSuccess(id, claimed, key, sha, now(clock)) == 1;
    } catch (Exception exception) {
      propQrCodeMapper.updatePropQrImageFailure(
          id,
          claimed,
          attempt < 5 ? now(clock).plusSeconds(BACKOFF[attempt - 1]) : null,
          now(clock));
      com.mujian.backend.common.api.FailureDiagnostics.background("prop_qr_image", id, exception);
      return false;
    }
  }

  /** 结束已耗尽重试次数的过期码图任务。 */
  private void expireExhausted() {
    propQrCodeMapper.updateExhaustedQrImageLeases(now(clock));
  }

  /** 按既定调度节奏处理待生成或租约已过期的码图任务。 */
  @Scheduled(fixedDelayString = "${mj.prop.qr-worker-delay-ms:5000}")
  public void scheduled() {
    if (!enabled) return;
    try {
      expireExhausted();
      for (PropQrCodeDO propQrCodeDO : propQrCodeMapper.selectDueQrImageListByTime(now(clock)))
        generate(propQrCodeDO.getId());
    } catch (Exception exception) {
      com.mujian.backend.common.api.FailureDiagnostics.background(
          "prop_qr_scan", com.mujian.backend.common.support.Ids.next(), exception);
    }
  }
}
