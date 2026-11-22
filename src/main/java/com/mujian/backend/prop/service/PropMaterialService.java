package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.prop.dto.ImageInputDTO;
import com.mujian.backend.prop.dto.ImageLocatorDTO;
import com.mujian.backend.prop.vo.ImageReadVO;
import com.mujian.backend.prop.vo.ImageViewVO;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import com.mujian.backend.prop.vo.UploadResultVO;
import java.time.*;
import java.util.*;

/** PropMaterialService业务入口；实现保留领域协作与事务边界。 */
public interface PropMaterialService {
  /** 校验上传用途后复用现有材料上传能力。 */
  UploadResultVO upload(String actor, String purpose, byte[] byteArray, boolean admin);

  /** 在进入业务事务前验证上传凭证和材料归属。 */
  void preflight(String actor, List<ImageInputDTO> imageInputDTOList, String purpose);

  /** 验证上传或历史材料引用，取得可保存的对象路径。 */
  String resolve(String actor, ImageInputDTO imageInputDTO, String purpose);

  /** 按来源、快照区段和序号校验材料归属与查看权限。 */
  String key(String actor, ImageLocatorDTO imageLocatorDTO, boolean admin);

  /** 为对象路径签发既定期限的图片访问链接。 */
  ImageViewVO sign(String key);

  /** 签发可选展示图片，存储暂不可用时按原规则返回空值。 */
  ImageViewVO optional(String key);

  /** 仅在公开道具准入校验后调用，签发30分钟链接和稳定cacheKey。 */
  PublicImageViewVO publicImage(String key);

  /** 验证私有材料权限并记录查看审计，再签发短时链接。 */
  ImageViewVO viewImage(String actor, ImageLocatorDTO imageLocatorDTO, boolean admin, String trace);

  /** 按原顺序组装材料定位信息及获授权的预览链接。 */
  List<ImageReadVO> images(
      List<String> keys, String source, String id, String section, boolean preview);

  /** 检查所有已保存的对象路径仍可被存储服务访问。 */
  void verifyStored(List<String> keys);
}
