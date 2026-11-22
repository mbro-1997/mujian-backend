package com.mujian.backend.casework.dto;

import com.mujian.backend.customer.vo.ServiceViews.Source;

public record CreateServiceCaseDTO(
      String userId, String demand, String mainConflict, String contextId, Source source) {}
