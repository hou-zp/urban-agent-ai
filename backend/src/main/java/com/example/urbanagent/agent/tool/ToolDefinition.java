package com.example.urbanagent.agent.tool;

import java.util.List;
import java.util.Set;

public record ToolDefinition(
        String name,
        String description,
        List<ToolParameterDefinition> parameters,
        Set<String> permissionTags,
        ToolRiskLevel riskLevel
) {
}
