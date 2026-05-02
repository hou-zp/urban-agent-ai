package com.example.urbanagent.agent.tool;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class SpringAiToolMetadataExporter {

    private final UrbanToolRegistry urbanToolRegistry;

    public SpringAiToolMetadataExporter(UrbanToolRegistry urbanToolRegistry) {
        this.urbanToolRegistry = urbanToolRegistry;
    }

    public List<ToolDefinition> export() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (ToolRegistration registration : urbanToolRegistry.list()) {
            Method method = resolveMethod(registration);
            definitions.add(new ToolDefinition(
                    registration.name(),
                    resolveDescription(registration, method),
                    extractParameters(method),
                    registration.permissionTags(),
                    registration.riskLevel()
            ));
        }
        return List.copyOf(definitions);
    }

    public ToolCallbackProvider createToolCallbackProvider() {
        Object[] toolObjects = urbanToolRegistry.list()
                .stream()
                .map(ToolRegistration::toolBean)
                .distinct()
                .toArray();
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects)
                .build();
    }

    private Method resolveMethod(ToolRegistration registration) {
        return Arrays.stream(registration.toolBean().getClass().getMethods())
                .filter(method -> matchesToolName(registration.name(), method))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到工具方法: " + registration.name()));
    }

    private boolean matchesToolName(String toolName, Method method) {
        Tool springAiTool = method.getAnnotation(Tool.class);
        if (springAiTool != null && !springAiTool.name().isBlank()) {
            return toolName.equals(springAiTool.name());
        }
        io.agentscope.core.tool.Tool agentScopeTool = method.getAnnotation(io.agentscope.core.tool.Tool.class);
        return agentScopeTool != null && toolName.equals(agentScopeTool.name());
    }

    private String resolveDescription(ToolRegistration registration, Method method) {
        Tool springAiTool = method.getAnnotation(Tool.class);
        if (springAiTool != null && !springAiTool.description().isBlank()) {
            return springAiTool.description();
        }
        io.agentscope.core.tool.Tool agentScopeTool = method.getAnnotation(io.agentscope.core.tool.Tool.class);
        if (agentScopeTool != null && !agentScopeTool.description().isBlank()) {
            return agentScopeTool.description();
        }
        return registration.description();
    }

    private List<ToolParameterDefinition> extractParameters(Method method) {
        List<ToolParameterDefinition> definitions = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            io.agentscope.core.tool.ToolParam agentScopeToolParam = parameter.getAnnotation(io.agentscope.core.tool.ToolParam.class);
            org.springframework.ai.tool.annotation.ToolParam springAiToolParam =
                    parameter.getAnnotation(org.springframework.ai.tool.annotation.ToolParam.class);
            String name = Optional.ofNullable(agentScopeToolParam)
                    .map(io.agentscope.core.tool.ToolParam::name)
                    .filter(value -> !value.isBlank())
                    .orElse(parameter.getName());
            String description = Optional.ofNullable(springAiToolParam)
                    .map(org.springframework.ai.tool.annotation.ToolParam::description)
                    .filter(value -> !value.isBlank())
                    .or(() -> Optional.ofNullable(agentScopeToolParam)
                            .map(io.agentscope.core.tool.ToolParam::description)
                            .filter(value -> !value.isBlank()))
                    .orElse("");
            boolean required = springAiToolParam == null || springAiToolParam.required();
            definitions.add(new ToolParameterDefinition(name, description, required));
        }
        return List.copyOf(definitions);
    }
}
