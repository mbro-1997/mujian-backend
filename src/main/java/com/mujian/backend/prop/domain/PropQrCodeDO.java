package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_qr_code：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropQrCodeDO extends BaseDO {

  /** 二维码业务编号 */
  private String qrCodeId;

  /** 码类型 */
  private String codeType;

  /** 指向类型 */
  private String targetType;

  /** 指向对象 ID */
  private String targetId;

  /** SKU ID */
  private String skuId;

  /** 替代的旧二维码 ID */
  private String supersedesQrCodeId;

  /** 小程序码 scene */
  private String qrScene;

  /** 小程序码页面 */
  private String qrPage;

  /** 二维码内容 */
  private String qrContent;

  /** 平台统一解析地址 */
  private String resolveUrl;

  /** 二维码图片 URL */
  private String qrImageUrl;

  /** 二维码图片存储 key */
  private String qrImageStorageKey;

  /** 图片 SHA256 */
  private String imageSha256;

  /** 二维码状态 */
  private String status;

  /** B类活动码唯一保护 */
  private String activeSkuGuard;

  /** A 类活动二维码守卫 */
  private String activeSnGuard;

  /** 创建管理员 ID */
  private String createdByAdminUserId;

  /** 使用工厂用户 ID */
  private String usedByFactoryUserId;

  /** 使用时间 */
  private LocalDateTime usedAt;

  /** 作废管理员 ID */
  private String revokedByAdminUserId;

  /** 作废时间 */
  private LocalDateTime revokedAt;

  /** 乐观锁版本 */
  private Integer version;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 码图片生成状态 */
  private String imageGenerationStatus;

  /** 自动生成尝试次数 */
  private Integer imageAttempts;

  /** 生成租约截止 */
  private LocalDateTime imageLeaseUntil;

  /** 下次生成时间 */
  private LocalDateTime imageNextRetryAt;

  /** 脱敏生成错误码 */
  private String imageErrorCode;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 读取二维码业务编号，供持久化映射使用。 */
  public String getQrCodeId() {
    return qrCodeId;
  }

  /** 接收二维码业务编号，保持持久化映射的字段含义。 */
  public void setQrCodeId(String value) {
    qrCodeId = value;
  }

  /** 读取码类型，供持久化映射使用。 */
  public String getCodeType() {
    return codeType;
  }

  /** 接收码类型，保持持久化映射的字段含义。 */
  public void setCodeType(String value) {
    codeType = value;
  }

  /** 读取指向类型，供持久化映射使用。 */
  public String getTargetType() {
    return targetType;
  }

  /** 接收指向类型，保持持久化映射的字段含义。 */
  public void setTargetType(String value) {
    targetType = value;
  }

  /** 读取指向对象 ID，供持久化映射使用。 */
  public String getTargetId() {
    return targetId;
  }

  /** 接收指向对象 ID，保持持久化映射的字段含义。 */
  public void setTargetId(String value) {
    targetId = value;
  }

  /** 读取SKU ID，供持久化映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收SKU ID，保持持久化映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取替代的旧二维码 ID，供持久化映射使用。 */
  public String getSupersedesQrCodeId() {
    return supersedesQrCodeId;
  }

  /** 接收替代的旧二维码 ID，保持持久化映射的字段含义。 */
  public void setSupersedesQrCodeId(String value) {
    supersedesQrCodeId = value;
  }

  /** 读取小程序码 scene，供持久化映射使用。 */
  public String getQrScene() {
    return qrScene;
  }

  /** 接收小程序码 scene，保持持久化映射的字段含义。 */
  public void setQrScene(String value) {
    qrScene = value;
  }

  /** 读取小程序码页面，供持久化映射使用。 */
  public String getQrPage() {
    return qrPage;
  }

  /** 接收小程序码页面，保持持久化映射的字段含义。 */
  public void setQrPage(String value) {
    qrPage = value;
  }

  /** 读取二维码内容，供持久化映射使用。 */
  public String getQrContent() {
    return qrContent;
  }

  /** 接收二维码内容，保持持久化映射的字段含义。 */
  public void setQrContent(String value) {
    qrContent = value;
  }

  /** 读取平台统一解析地址，供持久化映射使用。 */
  public String getResolveUrl() {
    return resolveUrl;
  }

  /** 接收平台统一解析地址，保持持久化映射的字段含义。 */
  public void setResolveUrl(String value) {
    resolveUrl = value;
  }

  /** 读取二维码图片 URL，供持久化映射使用。 */
  public String getQrImageUrl() {
    return qrImageUrl;
  }

  /** 接收二维码图片 URL，保持持久化映射的字段含义。 */
  public void setQrImageUrl(String value) {
    qrImageUrl = value;
  }

  /** 读取二维码图片存储 key，供持久化映射使用。 */
  public String getQrImageStorageKey() {
    return qrImageStorageKey;
  }

  /** 接收二维码图片存储 key，保持持久化映射的字段含义。 */
  public void setQrImageStorageKey(String value) {
    qrImageStorageKey = value;
  }

  /** 读取图片 SHA256，供持久化映射使用。 */
  public String getImageSha256() {
    return imageSha256;
  }

  /** 接收图片 SHA256，保持持久化映射的字段含义。 */
  public void setImageSha256(String value) {
    imageSha256 = value;
  }

  /** 读取二维码状态，供持久化映射使用。 */
  public String getStatus() {
    return status;
  }

  /** 接收二维码状态，保持持久化映射的字段含义。 */
  public void setStatus(String value) {
    status = value;
  }

  /** 读取B类活动码唯一保护，供持久化映射使用。 */
  public String getActiveSkuGuard() {
    return activeSkuGuard;
  }

  /** 接收B类活动码唯一保护，保持持久化映射的字段含义。 */
  public void setActiveSkuGuard(String value) {
    activeSkuGuard = value;
  }

  /** 读取A 类活动二维码守卫，供持久化映射使用。 */
  public String getActiveSnGuard() {
    return activeSnGuard;
  }

  /** 接收A 类活动二维码守卫，保持持久化映射的字段含义。 */
  public void setActiveSnGuard(String value) {
    activeSnGuard = value;
  }

  /** 读取创建管理员 ID，供持久化映射使用。 */
  public String getCreatedByAdminUserId() {
    return createdByAdminUserId;
  }

  /** 接收创建管理员 ID，保持持久化映射的字段含义。 */
  public void setCreatedByAdminUserId(String value) {
    createdByAdminUserId = value;
  }

  /** 读取使用工厂用户 ID，供持久化映射使用。 */
  public String getUsedByFactoryUserId() {
    return usedByFactoryUserId;
  }

  /** 接收使用工厂用户 ID，保持持久化映射的字段含义。 */
  public void setUsedByFactoryUserId(String value) {
    usedByFactoryUserId = value;
  }

  /** 读取使用时间，供持久化映射使用。 */
  public LocalDateTime getUsedAt() {
    return usedAt;
  }

  /** 接收使用时间，保持持久化映射的字段含义。 */
  public void setUsedAt(LocalDateTime value) {
    usedAt = value;
  }

  /** 读取作废管理员 ID，供持久化映射使用。 */
  public String getRevokedByAdminUserId() {
    return revokedByAdminUserId;
  }

  /** 接收作废管理员 ID，保持持久化映射的字段含义。 */
  public void setRevokedByAdminUserId(String value) {
    revokedByAdminUserId = value;
  }

  /** 读取作废时间，供持久化映射使用。 */
  public LocalDateTime getRevokedAt() {
    return revokedAt;
  }

  /** 接收作废时间，保持持久化映射的字段含义。 */
  public void setRevokedAt(LocalDateTime value) {
    revokedAt = value;
  }

  /** 读取乐观锁版本，供持久化映射使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持持久化映射的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取创建时间，供持久化映射使用。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持持久化映射的字段含义。 */
  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  /** 读取码图片生成状态，供持久化映射使用。 */
  public String getImageGenerationStatus() {
    return imageGenerationStatus;
  }

  /** 接收码图片生成状态，保持持久化映射的字段含义。 */
  public void setImageGenerationStatus(String value) {
    imageGenerationStatus = value;
  }

  /** 读取自动生成尝试次数，供持久化映射使用。 */
  public Integer getImageAttempts() {
    return imageAttempts;
  }

  /** 接收自动生成尝试次数，保持持久化映射的字段含义。 */
  public void setImageAttempts(Integer value) {
    imageAttempts = value;
  }

  /** 读取生成租约截止，供持久化映射使用。 */
  public LocalDateTime getImageLeaseUntil() {
    return imageLeaseUntil;
  }

  /** 接收生成租约截止，保持持久化映射的字段含义。 */
  public void setImageLeaseUntil(LocalDateTime value) {
    imageLeaseUntil = value;
  }

  /** 读取下次生成时间，供持久化映射使用。 */
  public LocalDateTime getImageNextRetryAt() {
    return imageNextRetryAt;
  }

  /** 接收下次生成时间，保持持久化映射的字段含义。 */
  public void setImageNextRetryAt(LocalDateTime value) {
    imageNextRetryAt = value;
  }

  /** 读取脱敏生成错误码，供持久化映射使用。 */
  public String getImageErrorCode() {
    return imageErrorCode;
  }

  /** 接收脱敏生成错误码，保持持久化映射的字段含义。 */
  public void setImageErrorCode(String value) {
    imageErrorCode = value;
  }

  /** 读取更新时间，供持久化映射使用。 */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** 接收更新时间，保持持久化映射的字段含义。 */
  public void setUpdatedAt(LocalDateTime value) {
    updatedAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置二维码业务编号并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO qrCodeId(String value) {
    setQrCodeId(value);
    return this;
  }

  /** 设置码类型并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO codeType(String value) {
    setCodeType(value);
    return this;
  }

  /** 设置指向类型并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO targetType(String value) {
    setTargetType(value);
    return this;
  }

  /** 设置指向对象 ID并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO targetId(String value) {
    setTargetId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置替代的旧二维码 ID并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO supersedesQrCodeId(String value) {
    setSupersedesQrCodeId(value);
    return this;
  }

  /** 设置小程序码 scene并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO qrScene(String value) {
    setQrScene(value);
    return this;
  }

  /** 设置小程序码页面并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO qrPage(String value) {
    setQrPage(value);
    return this;
  }

  /** 设置二维码内容并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO qrContent(String value) {
    setQrContent(value);
    return this;
  }

  /** 设置平台统一解析地址并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO resolveUrl(String value) {
    setResolveUrl(value);
    return this;
  }

  /** 设置二维码图片 URL并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO qrImageUrl(String value) {
    setQrImageUrl(value);
    return this;
  }

  /** 设置二维码图片存储 key并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO qrImageStorageKey(String value) {
    setQrImageStorageKey(value);
    return this;
  }

  /** 设置图片 SHA256并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO imageSha256(String value) {
    setImageSha256(value);
    return this;
  }

  /** 设置二维码状态并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO status(String value) {
    setStatus(value);
    return this;
  }

  /** 设置B类活动码唯一保护并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO activeSkuGuard(String value) {
    setActiveSkuGuard(value);
    return this;
  }

  /** 设置A 类活动二维码守卫并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO activeSnGuard(String value) {
    setActiveSnGuard(value);
    return this;
  }

  /** 设置创建管理员 ID并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO createdByAdminUserId(String value) {
    setCreatedByAdminUserId(value);
    return this;
  }

  /** 设置使用工厂用户 ID并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO usedByFactoryUserId(String value) {
    setUsedByFactoryUserId(value);
    return this;
  }

  /** 设置使用时间并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO usedAt(LocalDateTime value) {
    setUsedAt(value);
    return this;
  }

  /** 设置作废管理员 ID并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO revokedByAdminUserId(String value) {
    setRevokedByAdminUserId(value);
    return this;
  }

  /** 设置作废时间并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO revokedAt(LocalDateTime value) {
    setRevokedAt(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置码图片生成状态并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO imageGenerationStatus(String value) {
    setImageGenerationStatus(value);
    return this;
  }

  /** 设置自动生成尝试次数并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO imageAttempts(Integer value) {
    setImageAttempts(value);
    return this;
  }

  /** 设置生成租约截止并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO imageLeaseUntil(LocalDateTime value) {
    setImageLeaseUntil(value);
    return this;
  }

  /** 设置下次生成时间并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO imageNextRetryAt(LocalDateTime value) {
    setImageNextRetryAt(value);
    return this;
  }

  /** 设置脱敏生成错误码并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO imageErrorCode(String value) {
    setImageErrorCode(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public PropQrCodeDO updatedAt(LocalDateTime value) {
    setUpdatedAt(value);
    return this;
  }
}
