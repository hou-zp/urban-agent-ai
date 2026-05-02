package com.example.urbanagent.agent.tool;

public record ToolParameterDefinition(
        String name,
        String description,
        boolean required
) {
}
