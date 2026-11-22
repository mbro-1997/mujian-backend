package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.dto.OrderQuery;
import com.mujian.backend.order.mapper.*;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.prop.service.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

/** 买家与工厂的租赁订单读取。 */
public interface RentalOrderReader {
  /** 取得供应方身份所属的工厂标识。 */
  String supplierFactory(String actor);

  /** 按买家或工厂的可见范围分页列出订单。 */
  PageResult<OrderViews.Summary> page(
      String actor, boolean supplier, String view, String keyword, PageQuery pageQueryPage);

  /** 管理调用者在外层核对菜单权限，业务列表和统计复用相同筛选。 */
  PageResult<OrderViews.Summary> readPage(OrderQuery orderQuery, boolean supplier);

  /** 读取当前角色可见的订单详情和可执行操作。 */
  OrderViews.Detail detail(String actor, String id, boolean supplier);

  /** 获权后从原批次快照取明细、配送、规则，当前SKU下架不抹去历史。 */
  OrderViews.Detail authorizedDetail(RentalOrderDO rentalOrderDO, boolean supplier);

  /** 统计当前工厂的租赁订单状态。 */
  OrderViews.Overview overview(String actor);
}
