package com.mujian.backend.prop.service;

import com.mujian.backend.common.enums.PropQrTargetType;
import com.mujian.backend.prop.domain.PropQrCodeDO;
import com.mujian.backend.prop.mapper.PropQrCodeMapper;
import com.mujian.backend.prop.policy.PropAccess;
import java.util.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import org.springframework.stereotype.Service;

/** Batch identities are derived from the replacement chain, never timestamp order. */
@Service
public class PropQrLookup {
  private final PropQrCodeMapper propQrCodeMapper;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropQrLookup(PropQrCodeMapper propQrCodeMapper) {
    this.propQrCodeMapper = propQrCodeMapper;
  }

  public record Chain(PropQrCodeDO current, PropQrCodeDO latest) {}

  /** 批量读取业务目标的二维码链并识别当前有效码。 */
  public Map<String, Chain> find(String type, List<String> targets) {
    HashMap<String, PropQrLookup.Chain> chainMap = new HashMap<String, Chain>();
    if (targets.isEmpty()) return chainMap;
    HashMap<String, List<PropQrCodeDO>> propQrCodeDOListMap =
        new HashMap<String, List<PropQrCodeDO>>();
    for (int start = 0; start < targets.size(); start += 500) {
      List<String> batchTargets = targets.subList(start, Math.min(start + 500, targets.size()));
      for (PropQrCodeDO propQrCodeDO :
          propQrCodeMapper.selectPropQrChainListByTargets(type, batchTargets)) {
        List<PropQrCodeDO> propQrCodeDOList = propQrCodeDOListMap.get(propQrCodeDO.getTargetId());
        if (propQrCodeDOList == null) {
          propQrCodeDOList = new ArrayList<>();
          propQrCodeDOListMap.put(propQrCodeDO.getTargetId(), propQrCodeDOList);
        }
        propQrCodeDOList.add(propQrCodeDO);
      }
    }
    for (Entry<String, List<PropQrCodeDO>> entry : propQrCodeDOListMap.entrySet()) {
      HashMap<String, PropQrCodeDO> propQrCodeDOMap = new HashMap<String, PropQrCodeDO>();
      HashMap<String, String> successors = new HashMap<String, String>();
      PropQrCodeDO propQrCodeDOActive = null;
      int roots = 0;
      String sku = null;
      for (PropQrCodeDO propQrCodeDO : entry.getValue()) {
        propQrCodeDOMap.put(propQrCodeDO.getId(), propQrCodeDO);
        if (sku != null && !sku.equals(propQrCodeDO.getSkuId())) throw PropAccess.inconsistent();
        sku = propQrCodeDO.getSkuId();
        if (type.equals(PropQrTargetType.SKU.name()) && !propQrCodeDO.getTargetId().equals(sku))
          throw PropAccess.inconsistent();
        if (propQrCodeDO.getSupersedesQrCodeId() == null) roots++;
        else if (successors.put(propQrCodeDO.getSupersedesQrCodeId(), propQrCodeDO.getId()) != null)
          throw PropAccess.inconsistent();
        if ("bound".equals(propQrCodeDO.getStatus())) {
          if (propQrCodeDOActive != null) throw PropAccess.inconsistent();
          propQrCodeDOActive = propQrCodeDO;
        }
      }
      List<PropQrCodeDO> propQrCodeDOList = new ArrayList<>();
      for (PropQrCodeDO propQrCodeDO : entry.getValue())
        if (!successors.containsKey(propQrCodeDO.getId())) propQrCodeDOList.add(propQrCodeDO);
      if (roots != 1 || propQrCodeDOList.size() != 1) throw PropAccess.inconsistent();
      PropQrCodeDO propQrCodeDOLatest = propQrCodeDOList.get(0);
      HashSet<String> visited = new HashSet<String>();
      PropQrCodeDO propQrCodeDONode = propQrCodeDOLatest;
      while (propQrCodeDONode != null) {
        if (!visited.add(propQrCodeDONode.getId())) throw PropAccess.inconsistent();
        String previous = propQrCodeDONode.getSupersedesQrCodeId();
        if (previous == null) break;
        propQrCodeDONode = propQrCodeDOMap.get(previous);
        if (propQrCodeDONode == null) throw PropAccess.inconsistent();
      }
      if (visited.size() != propQrCodeDOMap.size()
          || (propQrCodeDOActive != null
              && !propQrCodeDOActive.getId().equals(propQrCodeDOLatest.getId())))
        throw PropAccess.inconsistent();
      chainMap.put(entry.getKey(), new Chain(propQrCodeDOActive, propQrCodeDOLatest));
    }
    return chainMap;
  }
}
