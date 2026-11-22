package com.mujian.backend.factory.dto;

import java.time.LocalDateTime;

/** 数据库分页条件；offset为long，now由服务端时钟提供。 */
public record FactoryPageQueryDTO(
    int page,
    int limit,
    long offset,
    String keyword,
    String status,
    LocalDateTime from,
    LocalDateTime to,
    LocalDateTime now) {}
