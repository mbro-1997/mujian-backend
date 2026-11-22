package com.mujian.backend.prop.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.prop.dto.PropBrowseQueryDTO;
import com.mujian.backend.prop.service.PropBrowseService;
import com.mujian.backend.prop.vo.PropCardVO;
import com.mujian.backend.prop.vo.RentableCardVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class PropBrowseController extends BaseController {
  private final PropBrowseService propBrowseService;
  private final SessionService sessionService;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  public PropBrowseController(PropBrowseService propBrowseService, SessionService sessionService) {
    this.propBrowseService = propBrowseService;
    this.sessionService = sessionService;
  }

  /** OpenAPI explode=true：只接受重复参数，不把单个值中的逗号解释成分隔符。 */
  @InitBinder
  void bindRepeatedCodes(org.springframework.web.bind.WebDataBinder webDataBinder) {
    webDataBinder.registerCustomEditor(
        java.util.List.class,
        new org.springframework.beans.propertyeditors.CustomCollectionEditor(java.util.List.class) {
          /** 保留风格参数原值，不将逗号拆成额外选项。 */
          @Override
          public void setAsText(String text) {
            setValue(text);
          }
        });
  }

  /** 查询公开道具列表并批量组装展示数据。 */
  @GetMapping("/api/public/props")
  public ApiResponse<PageResult<PropCardVO>> browse(
      HttpServletRequest httpServletRequest,
      @ModelAttribute PropBrowseQueryDTO propBrowseQueryDTO) {
    return success(httpServletRequest, propBrowseService.browse(propBrowseQueryDTO));
  }

  /** 校验租赁方身份后查询匹配道具。 */
  @GetMapping("/api/me/props")
  public ApiResponse<PageResult<PropCardVO>> search(
      HttpServletRequest httpServletRequest,
      @ModelAttribute PropBrowseQueryDTO propBrowseQueryDTO) {
    return success(
        httpServletRequest,
        propBrowseService.search(
            sessionService.user(sessionService.from(httpServletRequest)), propBrowseQueryDTO));
  }

  /** 计算全体候选的租期可租量，完成全局排序后分页。 */
  @GetMapping("/api/me/props/rentable")
  public ApiResponse<PageResult<RentableCardVO>> rentable(
      HttpServletRequest httpServletRequest,
      @ModelAttribute PropBrowseQueryDTO propBrowseQueryDTO) {
    return success(
        httpServletRequest,
        propBrowseService.rentable(
            sessionService.user(sessionService.from(httpServletRequest)), propBrowseQueryDTO));
  }
}
