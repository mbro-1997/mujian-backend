package com.mujian.backend.settlement.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.settlement.dto.SettlementQuery;
import com.mujian.backend.settlement.service.SettlementReader;
import com.mujian.backend.settlement.vo.SettlementViews.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
public class SettlementController extends BaseController {
  private final SettlementReader settlementReader;
  private final TradingHttp tradingHttp;

  /** 装配资金追溯入口所需依赖。 */
  public SettlementController(SettlementReader settlementReader, TradingHttp tradingHttp) {
    this.settlementReader = settlementReader;
    this.tradingHttp = tradingHttp;
  }

  /** 分页查询当前工厂已实际到账的批次。 */
  @GetMapping("/api/me/factory-settlements")
  public ApiResponse<PageResult<Summary>> factoryList(
      HttpServletRequest r,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        r,
        settlementReader.list(
            tradingHttp.actor(r),
            false,
            new SettlementQuery(null, null, fromDate, toDate, PageQuery.of(page, pageSize))));
  }

  /** 按当前工厂归属查看指定记录与实际结果。 */
  @GetMapping("/api/me/factory-settlements/{batchId}")
  public ApiResponse<Detail> factoryDetail(HttpServletRequest r, @PathVariable String batchId) {
    return success(r, settlementReader.detail(tradingHttp.actor(r), false, batchId));
  }

  /** 仅允许读取当前工厂已结批次的订单明细。 */
  @GetMapping("/api/me/factory-settlements/{batchId}/items")
  public ApiResponse<PageResult<Item>> factoryItems(
      HttpServletRequest r,
      @PathVariable String batchId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        r,
        settlementReader.items(tradingHttp.actor(r), false, batchId, PageQuery.of(page, pageSize)));
  }

  /** 仅允许追溯当前工厂已结批次的执行记录。 */
  @GetMapping("/api/me/factory-settlements/{batchId}/executions")
  public ApiResponse<PageResult<Execution>> factoryExecutions(
      HttpServletRequest r,
      @PathVariable String batchId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        r,
        settlementReader.executions(
            tradingHttp.actor(r), false, batchId, PageQuery.of(page, pageSize)));
  }

  /** 按权限、时间和处理状态筛选结算批次。 */
  @GetMapping("/api/admin/settlements")
  public ApiResponse<PageResult<Summary>> adminList(
      HttpServletRequest r,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate,
      @RequestParam(required = false) String factoryId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        r,
        settlementReader.list(
            tradingHttp.actor(r),
            true,
            new SettlementQuery(
                factoryId, status, fromDate, toDate, PageQuery.of(page, pageSize))));
  }

  /** 按管理读取权限展示指定记录与追溯事实。 */
  @GetMapping("/api/admin/settlements/{batchId}")
  public ApiResponse<Detail> adminDetail(HttpServletRequest r, @PathVariable String batchId) {
    return success(r, settlementReader.detail(tradingHttp.actor(r), true, batchId));
  }

  /** 按读取权限分页查询批次订单明细。 */
  @GetMapping("/api/admin/settlements/{batchId}/items")
  public ApiResponse<PageResult<Item>> adminItems(
      HttpServletRequest r,
      @PathVariable String batchId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        r,
        settlementReader.items(tradingHttp.actor(r), true, batchId, PageQuery.of(page, pageSize)));
  }

  /** 按读取权限分页追溯批次的每笔执行。 */
  @GetMapping("/api/admin/settlements/{batchId}/executions")
  public ApiResponse<PageResult<Execution>> adminExecutions(
      HttpServletRequest r,
      @PathVariable String batchId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        r,
        settlementReader.executions(
            tradingHttp.actor(r), true, batchId, PageQuery.of(page, pageSize)));
  }

  /** 复核查看人和批次快照后保存一次已阅审计。 */
  @PostMapping("/api/admin/settlements/{batchId}/acknowledgements")
  public ApiResponse<Acknowledgement> acknowledge(
      HttpServletRequest r,
      @PathVariable String batchId,
      @RequestBody SettlementQuery.Acknowledge input) {
    return success(
        r,
        settlementReader.acknowledge(
            tradingHttp.command(r, TradingOperation.acknowledgeSettlement), batchId, input));
  }
}
