package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 ImageRead，不是数据库实体。 */
public class ImageReadVO {
  private ImageLocatorVO locator;

  private ImageViewVO preview;

  /** 读取私有图片定位信息，供接口结果展示使用。 */
  public ImageLocatorVO getLocator() {
    return locator;
  }

  /** 接收私有图片定位信息，保持接口结果展示的字段含义。 */
  public void setLocator(ImageLocatorVO imageLocatorVO) {
    locator = imageLocatorVO;
  }

  /** 读取获授权的图片预览，供接口结果展示使用。 */
  public ImageViewVO getPreview() {
    return preview;
  }

  /** 接收获授权的图片预览，保持接口结果展示的字段含义。 */
  public void setPreview(ImageViewVO imageViewVO) {
    preview = imageViewVO;
  }

  /** 设置私有图片定位信息并返回当前对象，供连续组装数据。 */
  public ImageReadVO locator(ImageLocatorVO imageLocatorVO) {
    setLocator(imageLocatorVO);
    return this;
  }

  /** 设置获授权的图片预览并返回当前对象，供连续组装数据。 */
  public ImageReadVO preview(ImageViewVO imageViewVO) {
    setPreview(imageViewVO);
    return this;
  }
}
