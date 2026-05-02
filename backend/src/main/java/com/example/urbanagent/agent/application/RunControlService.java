package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.domain.AgentRun;
import com.example.urbanagent.agent.domain.RunStatus;
import com.example.urbanagent.agent.repository.AgentRunRepository;
import com.example.urbanagent.common.config.RuntimeControlProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RunControlService {

    private final RuntimeControlProperties properties;
    private final AgentRunRepository agentRunRepository;
    private final Map<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    public RunControlService(RuntimeControlProperties properties, AgentRunRepository agentRunRepository) {
        this.properties = properties;
        this.agentRunRepository = agentRunRepository;
    }

    public void register(AgentRun run, SseEmitter emitter) {
        long timeoutMs = emitter == null ? properties.getRequestTimeoutMs() : properties.getStreamTimeoutMs();
        activeRuns.put(run.getId(), new ActiveRun(run.getId(), run.getSessionId(), run.getQuestion(), Instant.now().toEpochMilli() + timeoutMs, emitter));
    }

    public long streamTimeoutMs() {
        return properties.getStreamTimeoutMs();
    }

    public void checkRunnable(String runId) {
        ActiveRun activeRun = activeRuns.get(runId);
        if (activeRun == null) {
            return;
        }
        if (activeRun.cancelled) {
            throw new BusinessException(ErrorCode.RUN_CANCELLED, "任务已取消");
        }
        if (Instant.now().toEpochMilli() > activeRun.deadlineAt) {
            activeRun.cancelled = true;
            throw new BusinessException(ErrorCode.RUN_TIMEOUT, "任务执行超时，请缩小范围后重试");
        }
    }

    public void pauseChunk(String runId) {
        ActiveRun activeRun = activeRuns.get(runId);
        if (activeRun == null || properties.getStreamChunkDelayMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.getStreamChunkDelayMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.RUN_CANCELLED, "任务已取消");
        }
    }

    public AgentRun cancelLatestRun(String sessionId) {
        AgentRun run = agentRunRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        if (run.getStatus() != RunStatus.RUNNING) {
            throw new BusinessException(ErrorCode.RUN_NOT_CANCELLABLE, "当前没有可取消的运行任务");
        }
        ActiveRun activeRun = activeRuns.get(run.getId());
        if (activeRun != null) {
            activeRun.cancelled = true;
            sendCancelEvent(activeRun.emitter, run.getId());
            activeRuns.remove(run.getId());
        }
        run.cancel();
        return agentRunRepository.save(run);
    }

    public AgentRun getLatestResumableRun(String sessionId) {
        AgentRun run = agentRunRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        if (run.getStatus() != RunStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.RUN_NOT_RESUMABLE, "当前没有可恢复的已取消任务");
        }
        return run;
    }

    public Optional<String> getQuestion(String runId) {
        return Optional.ofNullable(activeRuns.get(runId)).map(activeRun -> activeRun.question);
    }

    public void complete(String runId) {
        activeRuns.remove(runId);
    }

    public void fail(String runId) {
        activeRuns.remove(runId);
    }

    private void sendCancelEvent(SseEmitter emitter, String runId) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("agent.cancelled").data("{\"runId\":\"" + runId + "\"}"));
            emitter.complete();
        } catch (IOException ignored) {
            emitter.completeWithError(ignored);
        }
    }

    private static final class ActiveRun {
        private final String runId;
        private final String sessionId;
        private final String question;
        private final long deadlineAt;
        private final SseEmitter emitter;
        private volatile boolean cancelled;

        private ActiveRun(String runId, String sessionId, String question, long deadlineAt, SseEmitter emitter) {
            this.runId = runId;
            this.sessionId = sessionId;
            this.question = question;
            this.deadlineAt = deadlineAt;
            this.emitter = emitter;
            this.cancelled = false;
        }
    }
}
