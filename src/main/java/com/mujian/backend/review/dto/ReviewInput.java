package com.mujian.backend.review.dto;

import java.util.List;

public record ReviewInput(
    String businessType, String businessOrderId, int rating, List<String> tags, String content) {}
