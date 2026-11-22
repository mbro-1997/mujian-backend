package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.prop.utils.PropPageUtils.*;

import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.domain.PropSkuStockDO;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.vo.AvailabilityVO;
import com.mujian.backend.prop.vo.MovementVO;
import com.mujian.backend.prop.vo.OwnedSkuVO;
import com.mujian.backend.prop.vo.PropDetailVO;
import com.mujian.backend.prop.vo.ReservationVO;
import com.mujian.backend.prop.vo.SnLogVO;
import com.mujian.backend.prop.vo.SnSummaryVO;
import com.mujian.backend.prop.vo.SnVO;
import com.mujian.backend.prop.vo.StockVO;
import java.util.*;
import org.springframework.transaction.annotation.*;

/** PropQueryService业务入口；实现保留领域协作与事务边界。 */
public interface PropQueryService {
  /** 判断道具当前是否满足公开浏览和租赁准入条件。 */
  boolean eligible(PropSkuDO propSkuDO);

  /** 校验道具可见性并返回公开资料与可选租期结果。 */
  PropDetailVO detail(String actor, String id, PropRentalPeriodQueryDTO propRentalPeriodQueryDTO);

  /** 区分管理员与工厂权限后取得可访问的道具。 */
  PropSkuDO authorized(String actor, String id, boolean admin);

  /** 读取并组装当前权限范围内的道具详情。 */
  OwnedSkuVO owned(String actor, String id, boolean admin);

  /** 分页查询工厂道具并批量补齐库存及二维码信息。 */
  PageResult<OwnedSkuVO> ownedList(String actor, OwnedQueryDTO ownedQueryDTO, boolean admin);

  /** 将库存台账转换为接口字段，不额外查询数据库。 */
  StockVO stockDto(PropSkuStockDO propSkuStockDO);

  /** 读取道具的库存台账并校验数据唯一性。 */
  StockVO stock(String actor, String id, boolean admin);

  /** 汇总实物状态数量供详情与审核页面展示。 */
  SnSummaryVO summaryDto(String sid);

  /** 校验实物道具访问权限后返回状态汇总。 */
  SnSummaryVO summary(String actor, String id, boolean admin);

  /** 分页查询实物资产并补齐二维码和可执行操作。 */
  PageResult<SnVO> instances(
      String actor, String id, InstanceQueryDTO instanceQueryDTO, boolean admin);

  /** 按时间及来源条件查询库存变更流水。 */
  PageResult<MovementVO> movements(
      String actor, String sid, MovementQueryDTO movementQueryDTO, boolean admin);

  /** 核对实物归属后查询状态变更历史。 */
  PageResult<SnLogVO> logs(String actor, String sn, LogQueryDTO logQueryDTO);

  /** 核对访问权限并计算指定道具的租期可租结果。 */
  AvailabilityVO availability(
      String actor, String sid, PropRentalPeriodQueryDTO propRentalPeriodQueryDTO);

  /** 校验道具与实物关联后查询库存占用记录。 */
  PageResult<ReservationVO> reservations(String actor, ReservationQueryDTO reservationQueryDTO);

  /** 核对工厂及可选道具归属后返回编辑选项。 */
  com.mujian.backend.prop.vo.OptionsVO ownedOptions(String actor, String skuId);
}
