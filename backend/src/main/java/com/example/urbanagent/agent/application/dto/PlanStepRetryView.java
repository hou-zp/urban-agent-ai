package com.example.urbanagent.agent.application.dto;

import java.util.List;

public record PlanStepRetryView(
        String action,
        String reason,
        List<Integer> prerequisiteStepOrders
) {
}
