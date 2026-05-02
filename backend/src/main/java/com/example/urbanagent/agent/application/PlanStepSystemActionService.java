package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.PlanStepSystemActionView;
import com.example.urbanagent.agent.domain.ToolCall;
import com.example.urbanagent.agent.repository.ToolCallRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlanStepSystemActionService {

    private static final String TOOL_DEPENDENCY_AUTORUN = "plan.dependency_autorun";
    private static final String TOOL_DEPENDENCY_RECOVER = "plan.dependency_recover";
    private static final Pattern LEGACY_TRIGGER_PATTERN = Pattern.compile("目标步骤\\s+(\\d+)\\.");
    private static final Pattern LEGACY_DEPENDENCY_PATTERN = Pattern.compile("前置步骤\\s+(\\d+)\\.\\s*(.+)$");

    private final ToolCallRepository toolCallRepository;

    public PlanStepSystemActionService(ToolCallRepository toolCallRepository) {
        this.toolCallRepository = toolCallRepository;
    }

    @Transactional(readOnly = true)
    public Map<Integer, List<PlanStepSystemActionView>> loadByRunId(String runId) {
        List<ToolCall> toolCalls = toolCallRepository.findByRunIdAndToolNameInOrderByCreatedAtAsc(
                runId,
                List.of(TOOL_DEPENDENCY_AUTORUN, TOOL_DEPENDENCY_RECOVER)
        );
        Map<Integer, List<PlanStepSystemActionView>> actionsByStep = new LinkedHashMap<>();
        for (ToolCall toolCall : toolCalls) {
            parse(toolCall).ifPresent(parsed -> actionsByStep
                    .computeIfAbsent(parsed.triggerStepOrder(), key -> new java.util.ArrayList<>())
                    .add(new PlanStepSystemActionView(
                            parsed.action(),
                            parsed.dependencyStepOrder(),
                            parsed.dependencyStepName(),
                            toolCall.getOutputSummary(),
                            toolCall.getCreatedAt()
                    )));
        }
        return actionsByStep.entrySet().stream()
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), List.copyOf(entry.getValue())),
                        LinkedHashMap::putAll);
    }

    private Optional<ParsedSystemAction> parse(ToolCall toolCall) {
        String action = switch (toolCall.getToolName()) {
            case TOOL_DEPENDENCY_AUTORUN -> "AUTORUN";
            case TOOL_DEPENDENCY_RECOVER -> "RECOVER";
            default -> null;
        };
        if (action == null) {
            return Optional.empty();
        }
        return parseStructured(toolCall.getInputSummary(), action)
                .or(() -> parseLegacy(toolCall.getInputSummary(), action));
    }

    private Optional<ParsedSystemAction> parseStructured(String inputSummary, String action) {
        if (inputSummary == null || inputSummary.isBlank() || !inputSummary.contains("=")) {
            return Optional.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : inputSummary.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = part.substring(0, separator).trim();
            String value = part.substring(separator + 1).trim();
            values.put(key, value);
        }
        Integer triggerStepOrder = parseInteger(values.get("triggerStepOrder"));
        Integer dependencyStepOrder = parseInteger(values.get("dependencyStepOrder"));
        String dependencyStepName = values.get("dependencyStepName");
        if (triggerStepOrder == null || dependencyStepOrder == null || dependencyStepName == null || dependencyStepName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedSystemAction(action, triggerStepOrder, dependencyStepOrder, dependencyStepName));
    }

    private Optional<ParsedSystemAction> parseLegacy(String inputSummary, String action) {
        if (inputSummary == null || inputSummary.isBlank()) {
            return Optional.empty();
        }
        Matcher triggerMatcher = LEGACY_TRIGGER_PATTERN.matcher(inputSummary);
        Matcher dependencyMatcher = LEGACY_DEPENDENCY_PATTERN.matcher(inputSummary);
        if (!triggerMatcher.find() || !dependencyMatcher.find()) {
            return Optional.empty();
        }
        Integer triggerStepOrder = parseInteger(triggerMatcher.group(1));
        Integer dependencyStepOrder = parseInteger(dependencyMatcher.group(1));
        String dependencyStepName = dependencyMatcher.group(2);
        if (triggerStepOrder == null || dependencyStepOrder == null || dependencyStepName == null || dependencyStepName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedSystemAction(action, triggerStepOrder, dependencyStepOrder, dependencyStepName.trim()));
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ParsedSystemAction(
            String action,
            Integer triggerStepOrder,
            Integer dependencyStepOrder,
            String dependencyStepName
    ) {
    }
}
