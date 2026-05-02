package com.example.urbanagent.common.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "urban-agent.runtime", name = "rate-limit-store", havingValue = "redis")
public class RedisRateLimitCounterStore implements RateLimitCounterStore {

    private static final String KEY_PREFIX = "urban-agent:rate-limit:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRateLimitCounterStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryAcquire(String scope, String userId, int limit, long nowMillis, long windowMillis) {
        long currentWindow = nowMillis / windowMillis;
        String key = KEY_PREFIX + scope + ":" + userId + ":" + currentWindow;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) {
            return false;
        }
        if (count == 1L) {
            long remainingMillis = windowMillis - (nowMillis % windowMillis) + 1_000L;
            stringRedisTemplate.expire(key, Duration.ofMillis(remainingMillis));
        }
        return count <= limit;
    }
}
