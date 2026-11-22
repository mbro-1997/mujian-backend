package com.mujian.backend.scheme.domain;

import java.time.LocalDateTime;

/** 不可变分享内容，组合唯一，链接独立引用。 */
public record SchemeShareSnapshotDO(
    String id,
    String schemeId,
    int contentVersion,
    boolean hidePrice,
    String snapshotJson,
    LocalDateTime createdAt) {}
