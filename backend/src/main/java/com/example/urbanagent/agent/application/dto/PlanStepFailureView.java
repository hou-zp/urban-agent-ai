package com.example.urbanagent.agent.application.dto;

import java.util.List;

public record PlanStepFailureView(
        PlanStepFailureCode errorCode,
        String category,
        String headline,
        String reason,
        String action,
        String actionLabel,
        PlanStepHandleCode handleCode,
        boolean dependencyBlocked,
        List<Integer> dependencyStepOrders
) {
}
