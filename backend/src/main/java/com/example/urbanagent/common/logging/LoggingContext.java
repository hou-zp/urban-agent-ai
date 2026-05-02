package com.example.urbanagent.common.logging;

import org.slf4j.MDC;

public final class LoggingContext {

    private LoggingContext() {
    }

    public static void put(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        MDC.put(key, value);
    }

    public static void remove(String key) {
        MDC.remove(key);
    }

    public static void clearRunScope() {
        MDC.remove("runId");
        MDC.remove("sessionId");
    }
}
