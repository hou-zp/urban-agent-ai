package com.example.urbanagent.agent.application.dto;

import java.util.List;

public record AgentTask(
        String taskCode,
        TaskType taskType,
        String name,
        String goal,
        boolean mandatory,
        List<Integer> dependencyOrders
) {
}
