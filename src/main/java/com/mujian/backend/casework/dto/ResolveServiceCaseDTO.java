package com.mujian.backend.casework.dto;

import com.mujian.backend.customer.vo.ServiceViews.Source;
import java.util.List;

public record ResolveServiceCaseDTO(int version, String resolutionSummary, List<Source> resultRefs) {}
