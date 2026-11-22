package com.mujian.backend.address.controller;

import com.mujian.backend.address.dto.AddressDtos.*;
import com.mujian.backend.address.service.AddressService;
import com.mujian.backend.address.vo.AddressViews.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.AddressStatus;
import com.mujian.backend.common.enums.PlanningOperation;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.service.*;
import com.mujian.backend.planning.vo.PlanningViews.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class AddressController extends BaseController {
  private final AddressService addressService;
  private final PlanningHttp planningHttp;

  /** 装配领域服务和统一会话适配。 */
  public AddressController(AddressService addressService, PlanningHttp planningHttp) {
    this.addressService = addressService;
    this.planningHttp = planningHttp;
  }

  /** 读取本人分页列表。 */
  @GetMapping("/api/me/addresses")
  public ApiResponse<PageResult<Address>> list(
      HttpServletRequest httpServletRequestRequest,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        httpServletRequestRequest,
        addressService.list(
            new PlanningQuery(
                planningHttp.actor(httpServletRequestRequest),
                AddressStatus.active.name(),
                PageQuery.of(page, pageSize))));
  }

  /** 读取本人详情。 */
  @GetMapping("/api/me/addresses/{id}")
  public ApiResponse<Address> detail(
      HttpServletRequest httpServletRequestRequest, @PathVariable String id) {
    return success(
        httpServletRequestRequest,
        addressService.detail(planningHttp.actor(httpServletRequestRequest), id));
  }

  /** 新增本人收货地址并处理默认地址唯一性。 */
  @PostMapping("/api/me/addresses")
  public ApiResponse<Receipt> createAddress(
      HttpServletRequest httpServletRequestRequest, @RequestBody CreateAddress createAddressInput) {
    return success(
        httpServletRequestRequest,
        addressService.create(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.createAddress),
            createAddressInput));
  }

  /** 保存本人地址内容，不修改登录绑定手机号。 */
  @PostMapping("/api/me/addresses/{id}/update")
  public ApiResponse<Receipt> updateAddress(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody UpdateAddress updateAddressInput) {
    return success(
        httpServletRequestRequest,
        addressService.update(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.updateAddress),
            id,
            updateAddressInput));
  }

  /** 将本人指定地址设为唯一默认地址。 */
  @PostMapping("/api/me/addresses/{id}/set-default")
  public ApiResponse<Receipt> setDefaultAddress(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody ExpectedVersion expectedVersion) {
    return success(
        httpServletRequestRequest,
        addressService.changeState(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.setDefaultAddress),
            id,
            expectedVersion,
            false));
  }

  /** 软删除本人地址并保留原请求可恢复结果。 */
  @PostMapping("/api/me/addresses/{id}/delete")
  public ApiResponse<Receipt> deleteAddress(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody ExpectedVersion expectedVersion) {
    return success(
        httpServletRequestRequest,
        addressService.changeState(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.deleteAddress),
            id,
            expectedVersion,
            true));
  }
}
