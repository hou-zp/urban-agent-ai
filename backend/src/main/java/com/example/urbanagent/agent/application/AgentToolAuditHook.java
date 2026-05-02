package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.domain.ToolCall;
import com.example.urbanagent.agent.repository.ToolCallRepository;
import com.example.urbanagent.agent.tool.AgentExecutionContextHolder;
import io.agentscope.core.hook.ErrorEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.TextBlock;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Component
public class AgentToolAuditHook implements Hook {

    private final ToolCallRepository toolCallRepository;

    public AgentToolAuditHook(ToolCallRepository toolCallRepository) {
        this.toolCallRepository = toolCallRepository;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreActingEvent preActingEvent) {
            onPreActing(preActingEvent);
        } else if (event instanceof PostActingEvent postActingEvent) {
            onPostActing(postActingEvent);
        } else if (event instanceof ErrorEvent errorEvent) {
            onError(errorEvent);
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 50;
    }

    private void onPreActing(PreActingEvent event) {
        String runId = AgentExecutionContextHolder.currentRunId();
        if (runId == null) {
            return;
        }
        ToolCall toolCall = toolCallRepository.save(new ToolCall(
                runId,
                event.getToolUse().getName(),
                truncate(String.valueOf(event.getToolUse().getInput())),
                "pending"
        ));
        AgentExecutionContextHolder.pushToolCallId(event.getToolUse().getName(), toolCall.getId());
    }

    private void onPostActing(PostActingEvent event) {
        String toolCallId = AgentExecutionContextHolder.popToolCallId(event.getToolUse().getName());
        if (toolCallId == null) {
            return;
        }
        toolCallRepository.findById(toolCallId).ifPresent(toolCall -> {
            String output = event.getToolResult().getOutput()
                    .stream()
                    .filter(TextBlock.class::isInstance)
                    .map(TextBlock.class::cast)
                    .map(TextBlock::getText)
                    .collect(Collectors.joining("\n"));
            toolCall.complete(truncate(output.isBlank() ? "empty result" : output));
            toolCallRepository.save(toolCall);
        });
    }

    private void onError(ErrorEvent event) {
        String runId = AgentExecutionContextHolder.currentRunId();
        if (runId == null) {
            return;
        }
        toolCallRepository.save(new ToolCall(
                runId,
                "agent.error",
                "agent execution failed",
                truncate(event.getError().getMessage())
        ));
    }

    private String truncate(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.length() <= 500 ? content : content.substring(0, 500);
    }
}
