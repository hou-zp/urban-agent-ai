package com.example.urbanagent.common.runtime;

import com.example.urbanagent.common.config.RuntimeControlProperties;
import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RequestRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final RuntimeControlProperties properties;
    private final RateLimitCounterStore rateLimitCounterStore;

    public RequestRateLimiter(RuntimeControlProperties properties,
                              RateLimitCounterStore rateLimitCounterStore) {
        this.properties = properties;
        this.rateLimitCounterStore = rateLimitCounterStore;
    }

    public void checkChatRequest() {
        check("chat", properties.getChatRequestsPerMinute());
    }

    public void checkQueryPreviewRequest() {
        check("query-preview", properties.getQueryPreviewRequestsPerMinute());
    }

    public void checkQueryExecuteRequest() {
        check("query-execute", properties.getQueryExecuteRequestsPerMinute());
    }

    private void check(String scope, int limit) {
        String userId = UserContextHolder.get().userId();
        if (!rateLimitCounterStore.tryAcquire(scope, userId, limit, Instant.now().toEpochMilli(), WINDOW_MILLIS)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后重试");
        }
    }
}
