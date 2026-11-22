package com.mujian.backend.prop.dto;

import java.util.List;

public record QrExceptionCreateDTO(
    String qrId, String exceptionType, String description, List<ImageInputDTO> evidence) {}
