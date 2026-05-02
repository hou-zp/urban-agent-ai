package com.example.urbanagent.agent.tool;

import com.example.urbanagent.agent.application.dto.MessageCitationView;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentExecutionContextHolder {

    private static final ThreadLocal<AgentExecutionContext> CONTEXT = new ThreadLocal<>();

    private AgentExecutionContextHolder() {
    }

    public static void open(String runId) {
        CONTEXT.set(new AgentExecutionContext(runId));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static String currentRunId() {
        AgentExecutionContext context = CONTEXT.get();
        return context == null ? null : context.runId();
    }

    public static void addCitation(MessageCitationView citation) {
        AgentExecutionContext context = CONTEXT.get();
        if (context == null || citation == null) {
            return;
        }
        context.citations().putIfAbsent(citation.documentId(), citation);
    }

    public static List<MessageCitationView> citations() {
        AgentExecutionContext context = CONTEXT.get();
        if (context == null) {
            return List.of();
        }
        return List.copyOf(context.citations().values());
    }

    public static void pushToolCallId(String toolName, String toolCallId) {
        AgentExecutionContext context = CONTEXT.get();
        if (context == null) {
            return;
        }
        context.pendingToolCallIds()
                .computeIfAbsent(toolName, key -> new ArrayDeque<>())
                .push(toolCallId);
    }

    public static String popToolCallId(String toolName) {
        AgentExecutionContext context = CONTEXT.get();
        if (context == null) {
            return null;
        }
        Deque<String> ids = context.pendingToolCallIds().get(toolName);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.pop();
    }

    private record AgentExecutionContext(
            String runId,
            Map<String, MessageCitationView> citations,
            Map<String, Deque<String>> pendingToolCallIds
    ) {

        private AgentExecutionContext(String runId) {
            this(runId, new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }
}
