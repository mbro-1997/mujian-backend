package com.mujian.backend.prop.vo;

import java.util.List;

public record QrExceptionPageVO(
    List<QrExceptionVO> items, String total, int page, int pageSize, QrExceptionCountsVO counts) {}
