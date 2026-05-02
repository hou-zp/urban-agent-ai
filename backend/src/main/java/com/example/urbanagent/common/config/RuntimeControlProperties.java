package com.example.urbanagent.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "urban-agent.runtime")
public class RuntimeControlProperties {

    @Min(value = 1, message = "chatRequestsPerMinute must be positive")
    private int chatRequestsPerMinute = 30;
    @Min(value = 1, message = "queryPreviewRequestsPerMinute must be positive")
    private int queryPreviewRequestsPerMinute = 60;
    @Min(value = 1, message = "queryExecuteRequestsPerMinute must be positive")
    private int queryExecuteRequestsPerMinute = 20;
    @Min(value = 1, message = "requestTimeoutMs must be positive")
    private long requestTimeoutMs = 15_000L;
    @Min(value = 1, message = "streamTimeoutMs must be positive")
    private long streamTimeoutMs = 30_000L;
    @Min(value = 1, message = "sqlQueryTimeoutSeconds must be positive")
    private int sqlQueryTimeoutSeconds = 30;
    @Min(value = 0, message = "streamChunkDelayMs must not be negative")
    private long streamChunkDelayMs = 10L;
    private String rateLimitStore = "memory";

    public int getChatRequestsPerMinute() {
        return chatRequestsPerMinute;
    }

    public void setChatRequestsPerMinute(int chatRequestsPerMinute) {
        this.chatRequestsPerMinute = chatRequestsPerMinute;
    }

    public int getQueryPreviewRequestsPerMinute() {
        return queryPreviewRequestsPerMinute;
    }

    public void setQueryPreviewRequestsPerMinute(int queryPreviewRequestsPerMinute) {
        this.queryPreviewRequestsPerMinute = queryPreviewRequestsPerMinute;
    }

    public int getQueryExecuteRequestsPerMinute() {
        return queryExecuteRequestsPerMinute;
    }

    public void setQueryExecuteRequestsPerMinute(int queryExecuteRequestsPerMinute) {
        this.queryExecuteRequestsPerMinute = queryExecuteRequestsPerMinute;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public long getStreamTimeoutMs() {
        return streamTimeoutMs;
    }

    public void setStreamTimeoutMs(long streamTimeoutMs) {
        this.streamTimeoutMs = streamTimeoutMs;
    }

    public int getSqlQueryTimeoutSeconds() {
        return sqlQueryTimeoutSeconds;
    }

    public void setSqlQueryTimeoutSeconds(int sqlQueryTimeoutSeconds) {
        this.sqlQueryTimeoutSeconds = sqlQueryTimeoutSeconds;
    }

    public long getStreamChunkDelayMs() {
        return streamChunkDelayMs;
    }

    public void setStreamChunkDelayMs(long streamChunkDelayMs) {
        this.streamChunkDelayMs = streamChunkDelayMs;
    }

    public String getRateLimitStore() {
        return rateLimitStore;
    }

    public void setRateLimitStore(String rateLimitStore) {
        this.rateLimitStore = rateLimitStore;
    }
}
