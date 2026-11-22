package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.order.service.*;
import com.mujian.backend.payment.domain.RefundOrderDO;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.vo.RefundViews;
import java.util.List;
import org.springframework.transaction.annotation.*;

/** 用户本人退款查询。 */
public interface RefundReader {
  /** 按退款标识和调用者归属读取退款详情。 */
  RefundViews.Refund owned(String actor, String id);

  /** 仅供外层已授权的管理查询使用，不接受HTTP传入buyer。 */
  RefundViews.Refund authorized(RefundOrderDO refundOrderDO);

  /** 一次读取全部原支付返券事实，列表行数不增加数据库往返。 */
  List<RefundViews.Refund> authorizedList(List<RefundOrderDO> refundOrderDOList);
}
