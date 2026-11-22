package com.mujian.backend.prop.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.dto.LabelConfirmDTO;
import com.mujian.backend.prop.dto.SnAdjustmentDTO;
import com.mujian.backend.prop.dto.StockAdjustmentDTO;
import com.mujian.backend.prop.dto.VersionReasonDTO;
import com.mujian.backend.prop.service.PropCommandService;
import com.mujian.backend.prop.service.PropQrService;
import com.mujian.backend.prop.service.PropQueryService;
import com.mujian.backend.prop.vo.AvailabilityVO;
import com.mujian.backend.prop.vo.CommandResultVO;
import com.mujian.backend.prop.vo.MovementVO;
import com.mujian.backend.prop.vo.ReservationVO;
import com.mujian.backend.prop.vo.SnLogVO;
import com.mujian.backend.prop.vo.SnSummaryVO;
import com.mujian.backend.prop.vo.SnVO;
import com.mujian.backend.prop.vo.StockVO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PropInventoryController extends PropEndpointController {
  private final PropQueryService propQueryService;
  private final PropCommandService propCommandService;
  private final PropQrService propQrService;

  /** 装配本业务接口所需服务，公共会话与追踪边界复用父类。 */
  public PropInventoryController(
      SessionService sessionService,
      PropQueryService propQueryService,
      PropCommandService propCommandService,
      PropQrService propQrService) {
    super(sessionService);
    this.propQueryService = propQueryService;
    this.propCommandService = propCommandService;
    this.propQrService = propQrService;
  }

  /** 返回当前工厂道具的实物资产列表。 */
  @GetMapping("/api/me/factory-props/{id}/instances")
  public ApiResponse<PageResult<SnVO>> ownPropInstances(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @ModelAttribute InstanceQueryDTO instanceQueryDTO) {
    return success(
        httpServletRequest,
        propQueryService.instances(actor(httpServletRequest), id, instanceQueryDTO, false));
  }

  /** 返回当前工厂实物资产的状态汇总。 */
  @GetMapping("/api/me/factory-props/{id}/instance-summary")
  public ApiResponse<SnSummaryVO> ownPropInstanceSummary(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest, propQueryService.summary(actor(httpServletRequest), id, false));
  }

  /** 返回当前工厂的库存台账。 */
  @GetMapping("/api/me/factory-props/{id}/stock")
  public ApiResponse<StockVO> ownPropStock(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest, propQueryService.stock(actor(httpServletRequest), id, false));
  }

  /** 返回当前工厂道具的库存变更流水。 */
  @GetMapping("/api/me/factory-props/{id}/movements")
  public ApiResponse<PageResult<MovementVO>> ownPropMovements(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @ModelAttribute MovementQueryDTO movementQueryDTO) {
    return success(
        httpServletRequest,
        propQueryService.movements(actor(httpServletRequest), id, movementQueryDTO, false));
  }

  /** 返回当前工厂实物的状态历史。 */
  @GetMapping("/api/me/prop-instances/{id}/logs")
  public ApiResponse<PageResult<SnLogVO>> ownPropInstanceLogs(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @ModelAttribute LogQueryDTO logQueryDTO) {
    return success(
        httpServletRequest, propQueryService.logs(actor(httpServletRequest), id, logQueryDTO));
  }

  /** 提交实物资产增减或维修申请。 */
  @PostMapping("/api/me/factory-props/{id}/sn-adjustments")
  public ApiResponse<CommandResultVO> submitSnAdjustment(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody SnAdjustmentDTO snAdjustmentDTO) {
    return success(
        httpServletRequest,
        propCommandService.snAdjustment(actor(httpServletRequest), id, key, snAdjustmentDTO));
  }

  /** 提交数量型库存调整申请。 */
  @PostMapping("/api/me/factory-props/{id}/stock-adjustments")
  public ApiResponse<CommandResultVO> submitStockAdjustment(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody StockAdjustmentDTO stockAdjustmentDTO) {
    return success(
        httpServletRequest,
        propCommandService.stockAdjustment(actor(httpServletRequest), id, key, stockAdjustmentDTO));
  }

  /** 提交实物维修完成操作。 */
  @PostMapping("/api/me/prop-instances/{id}/repair-complete")
  public ApiResponse<CommandResultVO> completePropRepair(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody VersionReasonDTO versionReasonDTO) {
    return success(
        httpServletRequest,
        propCommandService.repair(actor(httpServletRequest), id, key, versionReasonDTO));
  }

  /** 提交资产贴标确认。 */
  @PostMapping("/api/me/prop-instances/{id}/label-confirm")
  public ApiResponse<CommandResultVO> confirmPropLabel(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody LabelConfirmDTO labelConfirmDTO) {
    return success(
        httpServletRequest,
        propQrService.label(actor(httpServletRequest), id, key, labelConfirmDTO));
  }

  /** 返回管理员可访问的实物资产列表。 */
  @GetMapping("/api/admin/prop-assets/{id}/instances")
  public ApiResponse<PageResult<SnVO>> adminPropInstances(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @ModelAttribute InstanceQueryDTO instanceQueryDTO) {
    return success(
        httpServletRequest,
        propQueryService.instances(actor(httpServletRequest), id, instanceQueryDTO, true));
  }

  /** 返回管理端实物资产状态汇总。 */
  @GetMapping("/api/admin/prop-assets/{id}/instance-summary")
  public ApiResponse<SnSummaryVO> adminPropInstanceSummary(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest, propQueryService.summary(actor(httpServletRequest), id, true));
  }

  /** 返回管理员可访问的库存台账。 */
  @GetMapping("/api/admin/prop-assets/{id}/stock")
  public ApiResponse<StockVO> adminPropStock(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, propQueryService.stock(actor(httpServletRequest), id, true));
  }

  /** 返回管理端库存变更流水。 */
  @GetMapping("/api/admin/prop-assets/{id}/movements")
  public ApiResponse<PageResult<MovementVO>> adminPropMovements(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @ModelAttribute MovementQueryDTO movementQueryDTO) {
    return success(
        httpServletRequest,
        propQueryService.movements(actor(httpServletRequest), id, movementQueryDTO, true));
  }

  /** 返回管理端指定租期的可租结果。 */
  @GetMapping("/api/admin/prop-assets/{id}/availability")
  public ApiResponse<AvailabilityVO> adminPropAvailability(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @ModelAttribute PropRentalPeriodQueryDTO propRentalPeriodQueryDTO) {
    return success(
        httpServletRequest,
        propQueryService.availability(actor(httpServletRequest), id, propRentalPeriodQueryDTO));
  }

  /** 查询道具或实物的库存占用记录。 */
  @GetMapping("/api/admin/inventory-reservations")
  public ApiResponse<PageResult<ReservationVO>> listInventoryReservations(
      HttpServletRequest httpServletRequest,
      @ModelAttribute ReservationQueryDTO reservationQueryDTO) {
    return success(
        httpServletRequest,
        propQueryService.reservations(actor(httpServletRequest), reservationQueryDTO));
  }
}
