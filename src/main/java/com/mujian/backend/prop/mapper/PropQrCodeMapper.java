package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.dto.PropWriteDTO;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropQrCodeMapper {

  /** 撤销旧二维码并保留其失效时间。 */
  int updatePropQrRevocation(@Param("command") PropWriteDTO.RevokeQr revokeQr);

  /** 写入贴标确认状态及时间。 */
  int updatePropQrLabelConfirmation(@Param("command") PropWriteDTO.ConfirmQrLabel confirmQrLabel);

  /** 重置允许重试的码图任务状态与租约。 */
  int updatePropQrImageRetry(@Param("command") PropWriteDTO.RetryQrImage retryQrImage);

  /** 保存业务二维码。 */
  void insertPropQrCode(@Param("row") PropQrCodeDO propQrCodeDO);

  /** 按记录标识读取业务二维码。 */
  PropQrCodeDO selectPropQrCode(@Param("id") String id, @Param("lock") boolean lock);

  /** 按版本抢占码图任务租约，避免重复生成。 */
  int updatePropQrImageLease(
      @Param("id") String id,
      @Param("version") int version,
      @Param("now") LocalDateTime now,
      @Param("leaseUntil") LocalDateTime leaseUntil);

  /** 在租约仍有效时发布码图对象路径。 */
  int updatePropQrImageSuccess(
      @Param("id") String id,
      @Param("version") int version,
      @Param("storageKey") String storageKey,
      @Param("sha256") String sha256,
      @Param("now") LocalDateTime now);

  /** 在租约仍归当前执行者时记录码图生成失败。 */
  int updatePropQrImageFailure(
      @Param("id") String id,
      @Param("version") int version,
      @Param("nextRetryAt") LocalDateTime nextRetryAt,
      @Param("now") LocalDateTime now);

  /** 将耗尽次数的过期租约置为失败。 */
  int updateExhaustedQrImageLeases(@Param("now") LocalDateTime now);

  /** 读取待执行或租约到期的码图任务。 */
  List<PropQrCodeDO> selectDueQrImageListByTime(@Param("now") LocalDateTime now);

  /** 批量读取业务目标的二维码替换链。 */
  List<PropQrCodeDO> selectPropQrChainListByTargets(
      @Param("targetType") String targetType, @Param("targetIds") List<String> targetIds);

  /** 按业务二维码编码读取码记录。 */
  List<PropQrCodeDO> selectPropQrCodeListByCode(@Param("qrCode") String qrCode);

  /** 按二维码编码锁定对应记录。 */
  List<PropQrCodeDO> selectPropQrCodeListByCodeForUpdate(@Param("qrCode") String qrCode);

  /** 锁定业务目标当前有效二维码，保护替换操作。 */
  List<PropQrCodeDO> selectActivePropQrCodeByTargetForUpdate(
      @Param("targetType") String targetType, @Param("targetId") String targetId);

  /** 统计业务目标历史二维码数量。 */
  long selectPropQrHistoryCountByTarget(
      @Param("targetType") String targetType, @Param("targetId") String targetId);

  /** 按本页标识批量读取PropQrCodeDO，复用审核条件所需的当前数据。 */
  List<PropQrCodeDO> selectPropQrCodeListByIds(@Param("ids") List<String> ids);
}
