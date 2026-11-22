package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 Qr，不是数据库实体。 */
public class QrVO {
  private String id;

  private String qrCode;

  private String codeType;

  private String targetType;

  private String targetId;

  private String skuId;

  private String status;

  private Integer version;

  private String imageStatus;

  private ImageViewVO image;

  private Boolean canRetryImage;

  private Boolean canConfirmLabel;

  private Integer snVersion;

  private String supersedesQrId;

  private String skuCode;

  private String propName;

  private String snCode;

  private String instanceStatus;

  /** 读取记录标识，供接口结果展示使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持接口结果展示的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取业务二维码编码，供接口结果展示使用。 */
  public String getQrCode() {
    return qrCode;
  }

  /** 接收业务二维码编码，保持接口结果展示的字段含义。 */
  public void setQrCode(String value) {
    qrCode = value;
  }

  /** 读取码类型，供接口结果展示使用。 */
  public String getCodeType() {
    return codeType;
  }

  /** 接收码类型，保持接口结果展示的字段含义。 */
  public void setCodeType(String value) {
    codeType = value;
  }

  /** 读取关联目标类型，供接口结果展示使用。 */
  public String getTargetType() {
    return targetType;
  }

  /** 接收关联目标类型，保持接口结果展示的字段含义。 */
  public void setTargetType(String value) {
    targetType = value;
  }

  /** 读取关联目标标识，供接口结果展示使用。 */
  public String getTargetId() {
    return targetId;
  }

  /** 接收关联目标标识，保持接口结果展示的字段含义。 */
  public void setTargetId(String value) {
    targetId = value;
  }

  /** 读取道具标识，供接口结果展示使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持接口结果展示的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取状态筛选条件，供接口结果展示使用。 */
  public String getStatus() {
    return status;
  }

  /** 接收状态筛选条件，保持接口结果展示的字段含义。 */
  public void setStatus(String value) {
    status = value;
  }

  /** 读取乐观锁版本，供接口结果展示使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持接口结果展示的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取码图生成状态，供接口结果展示使用。 */
  public String getImageStatus() {
    return imageStatus;
  }

  /** 接收码图生成状态，保持接口结果展示的字段含义。 */
  public void setImageStatus(String value) {
    imageStatus = value;
  }

  /** 读取二维码签名图片，供接口结果展示使用。 */
  public ImageViewVO getImage() {
    return image;
  }

  /** 接收二维码签名图片，保持接口结果展示的字段含义。 */
  public void setImage(ImageViewVO imageViewVO) {
    image = imageViewVO;
  }

  /** 读取是否允许重试码图生成，供接口结果展示使用。 */
  public Boolean getCanRetryImage() {
    return canRetryImage;
  }

  /** 接收是否允许重试码图生成，保持接口结果展示的字段含义。 */
  public void setCanRetryImage(Boolean value) {
    canRetryImage = value;
  }

  /** 读取是否允许确认贴标，供接口结果展示使用。 */
  public Boolean getCanConfirmLabel() {
    return canConfirmLabel;
  }

  /** 接收是否允许确认贴标，保持接口结果展示的字段含义。 */
  public void setCanConfirmLabel(Boolean value) {
    canConfirmLabel = value;
  }

  /** 读取实物资产版本号，供接口结果展示使用。 */
  public Integer getSnVersion() {
    return snVersion;
  }

  /** 接收实物资产版本号，保持接口结果展示的字段含义。 */
  public void setSnVersion(Integer value) {
    snVersion = value;
  }

  /** 读取被当前码替代的原码标识，供接口结果展示使用。 */
  public String getSupersedesQrId() {
    return supersedesQrId;
  }

  /** 接收被当前码替代的原码标识，保持接口结果展示的字段含义。 */
  public void setSupersedesQrId(String value) {
    supersedesQrId = value;
  }

  /** 读取SKU 编码，供接口结果展示使用。 */
  public String getSkuCode() {
    return skuCode;
  }

  /** 接收SKU 编码，保持接口结果展示的字段含义。 */
  public void setSkuCode(String value) {
    skuCode = value;
  }

  /** 读取道具名称，供接口结果展示使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持接口结果展示的字段含义。 */
  public void setPropName(String value) {
    propName = value;
  }

  /** 读取SN 编号，供接口结果展示使用。 */
  public String getSnCode() {
    return snCode;
  }

  /** 接收SN 编号，保持接口结果展示的字段含义。 */
  public void setSnCode(String value) {
    snCode = value;
  }

  /** 读取实物状态，供接口结果展示使用。 */
  public String getInstanceStatus() {
    return instanceStatus;
  }

  /** 接收实物状态，保持接口结果展示的字段含义。 */
  public void setInstanceStatus(String value) {
    instanceStatus = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public QrVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置业务二维码编码并返回当前对象，供连续组装数据。 */
  public QrVO qrCode(String value) {
    setQrCode(value);
    return this;
  }

  /** 设置码类型并返回当前对象，供连续组装数据。 */
  public QrVO codeType(String value) {
    setCodeType(value);
    return this;
  }

  /** 设置关联目标类型并返回当前对象，供连续组装数据。 */
  public QrVO targetType(String value) {
    setTargetType(value);
    return this;
  }

  /** 设置关联目标标识并返回当前对象，供连续组装数据。 */
  public QrVO targetId(String value) {
    setTargetId(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public QrVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置状态筛选条件并返回当前对象，供连续组装数据。 */
  public QrVO status(String value) {
    setStatus(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public QrVO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置码图生成状态并返回当前对象，供连续组装数据。 */
  public QrVO imageStatus(String value) {
    setImageStatus(value);
    return this;
  }

  /** 设置二维码签名图片并返回当前对象，供连续组装数据。 */
  public QrVO image(ImageViewVO imageViewVO) {
    setImage(imageViewVO);
    return this;
  }

  /** 设置是否允许重试码图生成并返回当前对象，供连续组装数据。 */
  public QrVO canRetryImage(Boolean value) {
    setCanRetryImage(value);
    return this;
  }

  /** 设置是否允许确认贴标并返回当前对象，供连续组装数据。 */
  public QrVO canConfirmLabel(Boolean value) {
    setCanConfirmLabel(value);
    return this;
  }

  /** 设置实物资产版本号并返回当前对象，供连续组装数据。 */
  public QrVO snVersion(Integer value) {
    setSnVersion(value);
    return this;
  }

  /** 设置被当前码替代的原码标识并返回当前对象，供连续组装数据。 */
  public QrVO supersedesQrId(String value) {
    setSupersedesQrId(value);
    return this;
  }

  /** 设置SKU 编码并返回当前对象，供连续组装数据。 */
  public QrVO skuCode(String value) {
    setSkuCode(value);
    return this;
  }

  /** 设置道具名称并返回当前对象，供连续组装数据。 */
  public QrVO propName(String value) {
    setPropName(value);
    return this;
  }

  /** 设置SN 编号并返回当前对象，供连续组装数据。 */
  public QrVO snCode(String value) {
    setSnCode(value);
    return this;
  }

  /** 设置实物状态并返回当前对象，供连续组装数据。 */
  public QrVO instanceStatus(String value) {
    setInstanceStatus(value);
    return this;
  }
}
