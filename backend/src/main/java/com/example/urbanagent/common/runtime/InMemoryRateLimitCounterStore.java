package com.example.urbanagent.common.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "urban-agent.runtime", name = "rate-limit-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimitCounterStore implements RateLimitCounterStore {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String scope, String userId, int limit, long nowMillis, long windowMillis) {
        String key = scope + ":" + userId;
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter());
        return counter.tryAcquire(limit, nowMillis, windowMillis);
    }

    private static final class WindowCounter {
        private long windowStart = 0L;
        private int count = 0;

        synchronized boolean tryAcquire(int limit, long nowMillis, long windowMillis) {
            long currentWindow = nowMillis / windowMillis;
            if (windowStart != currentWindow) {
                windowStart = currentWindow;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }
    }
}
