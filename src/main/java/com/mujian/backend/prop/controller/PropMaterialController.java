package com.mujian.backend.prop.controller;

import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.ImageContent;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.dto.ImageLocatorDTO;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.vo.ImageViewVO;
import com.mujian.backend.prop.vo.UploadResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.util.*;
import java.util.HashSet;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PropMaterialController extends PropEndpointController {
  private final PropMaterialService propMaterialService;

  /** 装配本业务接口所需服务，公共会话与追踪边界复用父类。 */
  public PropMaterialController(
      SessionService sessionService, PropMaterialService propMaterialService) {
    super(sessionService);
    this.propMaterialService = propMaterialService;
  }

  /** 校验上传部件、用途和大小后调用材料服务。 */
  private UploadResultVO upload(
      HttpServletRequest httpServletRequest,
      MultipartFile multipartFile,
      String purpose,
      boolean admin)
      throws Exception {
    if (httpServletRequest.getQueryString() != null) throw ApiFailure.invalid();
    HashSet<String> parts = new HashSet<String>();
    for (Part part : httpServletRequest.getParts())
      if (!parts.add(part.getName())) throw ApiFailure.invalid();
    if (!parts.equals(Set.of("file", "purpose"))) throw ApiFailure.invalid();
    if (multipartFile.getSize() > 5242880)
      throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_TOO_LARGE);
    return propMaterialService.upload(
        actor(httpServletRequest), purpose, multipartFile.getBytes(), admin);
  }

  /** 接收工厂上传的道具材料。 */
  @PostMapping("/api/me/prop-materials")
  public ApiResponse<UploadResultVO> uploadOwnPropMaterial(
      HttpServletRequest httpServletRequest,
      @RequestParam("file") MultipartFile multipartFile,
      @RequestParam String purpose)
      throws Exception {
    return success(httpServletRequest, upload(httpServletRequest, multipartFile, purpose, false));
  }

  /** 校验归属后返回工厂私有材料访问链接。 */
  @PostMapping("/api/me/prop-materials/view")
  public ApiResponse<ImageViewVO> viewOwnPropImage(
      HttpServletRequest httpServletRequest, @RequestBody ImageLocatorDTO imageLocatorDTO) {
    return success(
        httpServletRequest,
        propMaterialService.viewImage(
            actor(httpServletRequest), imageLocatorDTO, false, trace(httpServletRequest)));
  }

  /** 接收管理员上传的二维码异常证据。 */
  @PostMapping("/api/admin/prop-materials")
  public ApiResponse<UploadResultVO> uploadAdminPropMaterial(
      HttpServletRequest httpServletRequest,
      @RequestParam("file") MultipartFile multipartFile,
      @RequestParam String purpose)
      throws Exception {
    return success(httpServletRequest, upload(httpServletRequest, multipartFile, purpose, true));
  }

  /** 审计管理员材料查看并返回私有链接。 */
  @PostMapping("/api/admin/prop-materials/view")
  public ApiResponse<ImageViewVO> viewAdminPropImage(
      HttpServletRequest httpServletRequest, @RequestBody ImageLocatorDTO imageLocatorDTO) {
    return success(
        httpServletRequest,
        propMaterialService.viewImage(
            actor(httpServletRequest), imageLocatorDTO, true, trace(httpServletRequest)));
  }

  /** 对同源管理端返回已授权的私有图片字节。 */
  @GetMapping("/api/admin/prop-materials/content")
  public ResponseEntity<byte[]> readAdminPropImage(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      @RequestParam String sourceType,
      @RequestParam String sourceId,
      @RequestParam String section,
      @RequestParam int index) {
    ImageContent imageContent = propMaterialService.readAdminImage(
        actor(httpServletRequest),
        new ImageLocatorDTO(sourceType, sourceId, section, index),
        trace(httpServletRequest));
    httpServletResponse.setCharacterEncoding((String) null);
    return ResponseEntity.ok()
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(imageContent.contentType()))
        .body(imageContent.bytes());
  }
}
