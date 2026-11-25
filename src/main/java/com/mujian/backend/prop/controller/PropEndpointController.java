package com.mujian.backend.prop.controller;

import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.ImageContent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.propertyeditors.CustomCollectionEditor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;

/** 仅承接HTTP身份、追踪和参数绑定，不包含业务授权规则。 */
public abstract class PropEndpointController extends BaseController {
  private final SessionService sessionService;

  /** 装配公共会话解析能力。 */
  protected PropEndpointController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  /** 从当前会话取得真实操作用户。 */
  protected String actor(HttpServletRequest request) {
    return sessionService.user(sessionService.from(request));
  }

  /** 读取请求追踪号供审计使用。 */
  protected String trace(HttpServletRequest request) {
    return (String) request.getAttribute("traceId");
  }

  /** 将业务已授权的图片以原媒体类型返回。 */
  protected ResponseEntity<byte[]> imageResponse(
      HttpServletResponse response, ImageContent image) {
    response.setCharacterEncoding((String) null);
    return ResponseEntity.ok()
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  /** 仅允许HTTP查询字段绑定，内部权限/SQL派生条件由服务端赋值。 */
  @InitBinder
  public void bindQuery(WebDataBinder webDataBinder) {
    webDataBinder.setAllowedFields(
        "page",
        "pageSize",
        "typeCode",
        "assetType",
        "factoryId",
        "skuStatus",
        "keyword",
        "factoryKeyword",
        "auditing",
        "hasPendingLabel",
        "status",
        "sourceType",
        "movementType",
        "startDate",
        "endDate",
        "skuId",
        "snId",
        "reviewCategory",
        "auditStatus",
        "exceptionType",
        "operation",
        "styleCodes");
    webDataBinder.registerCustomEditor(
        List.class,
        new CustomCollectionEditor(List.class) {
          /** 重复query参数才是多选，不将逗号字符串拆成列表。 */
          @Override
          public void setAsText(String text) {
            setValue(text);
          }
        });
  }
}
