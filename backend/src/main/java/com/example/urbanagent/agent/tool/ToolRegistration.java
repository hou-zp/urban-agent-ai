package com.example.urbanagent.agent.tool;

import java.util.Set;

public record ToolRegistration(
        String name,
        String description,
        Set<String> permissionTags,
        ToolRiskLevel riskLevel,
        Object toolBean
) {
}
