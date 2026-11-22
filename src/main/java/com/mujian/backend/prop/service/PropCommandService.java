package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.prop.dto.CreateSkuDTO;
import com.mujian.backend.prop.dto.EditOptionsDTO;
import com.mujian.backend.prop.dto.ResubmitSkuDTO;
import com.mujian.backend.prop.dto.SnAdjustmentDTO;
import com.mujian.backend.prop.dto.StatusApplicationDTO;
import com.mujian.backend.prop.dto.StockAdjustmentDTO;
import com.mujian.backend.prop.dto.VersionReasonDTO;
import com.mujian.backend.prop.vo.CommandResultVO;
import java.util.*;

/** PropCommandService业务入口；实现保留领域协作与事务边界。 */
public interface PropCommandService {
  /** 校验防重和材料后，在同一事务中创建道具及首次上架申请。 */
  CommandResultVO create(String actor, String key, CreateSkuDTO createSkuDTO);

  /** 恢复或重新提交被驳回、已撤回的道具申请。 */
  CommandResultVO resubmit(String actor, String skuId, String key, ResubmitSkuDTO resubmitSkuDTO);

  /** 提交已批准道具的需审核资料更新。 */
  CommandResultVO change(
      String actor,
      String skuId,
      String key,
      com.mujian.backend.prop.dto.SkuChangeDTO skuChangeDTO);

  /** 按版本修改道具分类、风格及运输建议，并记录幂等结果。 */
  CommandResultVO options(String actor, String sid, String key, EditOptionsDTO editOptionsDTO);

  /** 校验道具版本与状态后提交上下架申请。 */
  CommandResultVO status(
      String actor, String sid, String key, StatusApplicationDTO statusApplicationDTO);

  /** 锁定实物资产并提交增减、维修或异常扣减申请。 */
  CommandResultVO snAdjustment(
      String actor, String sid, String key, SnAdjustmentDTO snAdjustmentDTO);

  /** 锁定库存台账并提交数量调整申请。 */
  CommandResultVO stockAdjustment(
      String actor, String sid, String key, StockAdjustmentDTO stockAdjustmentDTO);

  /** 校验维修状态后恢复资产可用状态并记录流水。 */
  CommandResultVO repair(String actor, String snId, String key, VersionReasonDTO versionReasonDTO);

  /** 按原锁序撤回待审申请并记录业务结果。 */
  CommandResultVO cancel(
      String actor, String type, String auditId, String key, VersionReasonDTO versionReasonDTO);
}
