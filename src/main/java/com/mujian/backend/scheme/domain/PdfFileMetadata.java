package com.mujian.backend.scheme.domain;

import java.time.LocalDateTime;

/** 私有成品定位及下载响应元数据，不携带生成快照。 */
public record PdfFileMetadata(String id, LocalDateTime fileExpireAt) {}
