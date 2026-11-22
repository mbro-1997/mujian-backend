package com.mujian.backend.prop.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.dto.CreateSkuDTO;
import com.mujian.backend.prop.dto.EditOptionsDTO;
import com.mujian.backend.prop.dto.ResubmitSkuDTO;
import com.mujian.backend.prop.dto.StatusApplicationDTO;
import com.mujian.backend.prop.service.PropCommandService;
import com.mujian.backend.prop.service.PropQueryService;
import com.mujian.backend.prop.vo.CommandResultVO;
import com.mujian.backend.prop.vo.OptionsVO;
import com.mujian.backend.prop.vo.OwnedSkuVO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PropSkuController extends PropEndpointController {
  private final PropQueryService propQueryService;
  private final PropCommandService propCommandService;

  /** 装配本业务接口所需服务，公共会话与追踪边界复用父类。 */
  public PropSkuController(
      SessionService sessionService,
      PropQueryService propQueryService,
      PropCommandService propCommandService) {
    super(sessionService);
    this.propQueryService = propQueryService;
    this.propCommandService = propCommandService;
  }

  /** 核对工厂及可选道具归属后返回编辑选项。 */
  @GetMapping("/api/me/factory-props/options")
  public ApiResponse<OptionsVO> ownPropOptions(
      HttpServletRequest httpServletRequest, @RequestParam(required = false) String skuId) {
    return success(
        httpServletRequest, propQueryService.ownedOptions(actor(httpServletRequest), skuId));
  }

  /** 返回当前工厂道具目录及分页信息。 */
  @GetMapping("/api/me/factory-props")
  public ApiResponse<PageResult<OwnedSkuVO>> listOwnProps(
      HttpServletRequest httpServletRequest, @ModelAttribute OwnedQueryDTO ownedQueryDTO) {
    return success(
        httpServletRequest,
        propQueryService.ownedList(actor(httpServletRequest), ownedQueryDTO, false));
  }

  /** 提交新增道具及首次上架申请。 */
  @PostMapping("/api/me/factory-props")
  public ApiResponse<CommandResultVO> createProp(
      HttpServletRequest httpServletRequest,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody CreateSkuDTO createSkuDTO) {
    return success(
        httpServletRequest,
        propCommandService.create(actor(httpServletRequest), key, createSkuDTO));
  }

  /** 返回当前工厂指定道具的资料及操作权限。 */
  @GetMapping("/api/me/factory-props/{id}")
  public ApiResponse<OwnedSkuVO> ownPropDetail(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest, propQueryService.owned(actor(httpServletRequest), id, false));
  }

  /** 重新提交被驳回或撤回的道具申请。 */
  @PostMapping("/api/me/factory-props/{id}/resubmit")
  public ApiResponse<CommandResultVO> resubmitProp(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody ResubmitSkuDTO resubmitSkuDTO) {
    return success(
        httpServletRequest,
        propCommandService.resubmit(actor(httpServletRequest), id, key, resubmitSkuDTO));
  }

  /** 提交需审核资料更新，沿原申请与恢复链路处理。 */
  @PostMapping("/api/me/factory-props/{id}/change-applications")
  public ApiResponse<CommandResultVO> submitSkuChange(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody SkuChangeDTO skuChangeDTO) {
    return success(
        httpServletRequest,
        propCommandService.change(actor(httpServletRequest), id, key, skuChangeDTO));
  }

  /** 提交道具选项修改并保持版本校验。 */
  @PostMapping("/api/me/factory-props/{id}/edit-options")
  public ApiResponse<CommandResultVO> editPropOptions(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody EditOptionsDTO editOptionsDTO) {
    return success(
        httpServletRequest,
        propCommandService.options(actor(httpServletRequest), id, key, editOptionsDTO));
  }

  /** 提交道具上下架申请。 */
  @PostMapping("/api/me/factory-props/{id}/status-applications")
  public ApiResponse<CommandResultVO> submitPropStatus(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody StatusApplicationDTO statusApplicationDTO) {
    return success(
        httpServletRequest,
        propCommandService.status(actor(httpServletRequest), id, key, statusApplicationDTO));
  }

  /** 按管理端权限查询工厂道具资产目录。 */
  @GetMapping("/api/admin/prop-assets")
  public ApiResponse<PageResult<OwnedSkuVO>> listPropAssets(
      HttpServletRequest httpServletRequest, @ModelAttribute OwnedQueryDTO ownedQueryDTO) {
    return success(
        httpServletRequest,
        propQueryService.ownedList(actor(httpServletRequest), ownedQueryDTO, true));
  }

  /** 返回管理员可访问的道具资料。 */
  @GetMapping("/api/admin/prop-assets/{id}")
  public ApiResponse<OwnedSkuVO> adminPropAsset(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, propQueryService.owned(actor(httpServletRequest), id, true));
  }
}
