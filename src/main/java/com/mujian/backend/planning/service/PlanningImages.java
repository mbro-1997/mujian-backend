package com.mujian.backend.planning.service;

import com.mujian.backend.planning.domain.SnapshotV1;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import java.util.*;
import org.springframework.stereotype.Service;

/** 仅由已验证资源归属的服务传入商品快照，复用30分钟公开图签名。 */
@Service
public class PlanningImages {
  private final PropMaterialService propMaterialService;

  /** 复用商品图片签名实现。 */
  public PlanningImages(PropMaterialService propMaterialService) {
    this.propMaterialService = propMaterialService;
  }

  /** 同一响应同路径只签发一次；历史商品下架不改变已获权快照。 */
  public Map<String, PublicImageViewVO> sign(List<SnapshotV1> snapshotV1ListSnapshots) {
    Map<String, PublicImageViewVO> publicImageViewVOMapImages = new HashMap<>();
    for (SnapshotV1 snapshotV1 : snapshotV1ListSnapshots)
      if (!snapshotV1.imageKeys().isEmpty()) {
        String key = snapshotV1.imageKeys().get(0);
        if (!publicImageViewVOMapImages.containsKey(key))
          publicImageViewVOMapImages.put(key, propMaterialService.publicImage(key));
      }
    return publicImageViewVOMapImages;
  }

  /** 从本次签名结果定位快照封面。 */
  public PublicImageViewVO cover(
      SnapshotV1 snapshotV1, Map<String, PublicImageViewVO> publicImageViewVOMapImages) {
    return snapshotV1.imageKeys().isEmpty()
        ? null
        : publicImageViewVOMapImages.get(snapshotV1.imageKeys().get(0));
  }
}
