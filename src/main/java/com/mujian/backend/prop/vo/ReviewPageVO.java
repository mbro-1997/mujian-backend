package com.mujian.backend.prop.vo;

import java.util.List;

public record ReviewPageVO(
    List<ReviewRowVO> items,
    String total,
    int page,
    int pageSize,
    ReviewPendingCountsVO pendingCounts) {}
