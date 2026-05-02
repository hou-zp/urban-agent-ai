package com.example.urbanagent.agent.application.dto;

import java.util.List;

public record AgentPlanGraph(
        ParsedQuestion parsedQuestion,
        List<AgentTask> tasks
) {
}
