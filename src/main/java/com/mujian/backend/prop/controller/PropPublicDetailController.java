package com.mujian.backend.prop.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.service.PropCatalogService;
import com.mujian.backend.prop.service.PropQueryService;
import com.mujian.backend.prop.vo.OptionsVO;
import com.mujian.backend.prop.vo.PropDetailVO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PropPublicDetailController extends PropEndpointController {
  private final PropCatalogService propCatalogService;
  private final PropQueryService propQueryService;

  /** 装配本业务接口所需服务，公共会话与追踪边界复用父类。 */
  public PropPublicDetailController(
      SessionService sessionService,
      PropCatalogService propCatalogService,
      PropQueryService propQueryService) {
    super(sessionService);
    this.propCatalogService = propCatalogService;
    this.propQueryService = propQueryService;
  }

  /** 返回公开道具筛选选项。 */
  @GetMapping("/api/public/props/options")
  public ApiResponse<OptionsVO> publicPropOptions(HttpServletRequest httpServletRequest) {
    return success(httpServletRequest, propCatalogService.options());
  }

  /** 返回公开道具详情。 */
  @GetMapping("/api/public/props/{id}")
  public ApiResponse<PropDetailVO> publicPropDetail(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @ModelAttribute PropRentalPeriodQueryDTO propRentalPeriodQueryDTO) {
    return success(httpServletRequest, propQueryService.detail(null, id, propRentalPeriodQueryDTO));
  }

  /** 校验租赁方身份后返回道具详情与租期结果。 */
  @GetMapping("/api/me/props/{id}")
  public ApiResponse<PropDetailVO> rentalPropDetail(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @ModelAttribute PropRentalPeriodQueryDTO propRentalPeriodQueryDTO) {
    return success(
        httpServletRequest,
        propQueryService.detail(actor(httpServletRequest), id, propRentalPeriodQueryDTO));
  }
}
