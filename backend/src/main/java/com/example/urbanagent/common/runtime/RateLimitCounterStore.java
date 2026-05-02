package com.example.urbanagent.common.runtime;

public interface RateLimitCounterStore {

    boolean tryAcquire(String scope, String userId, int limit, long nowMillis, long windowMillis);
}
