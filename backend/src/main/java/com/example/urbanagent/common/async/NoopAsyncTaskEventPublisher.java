package com.example.urbanagent.common.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "urban-agent.async", name = "publisher", havingValue = "none", matchIfMissing = true)
public class NoopAsyncTaskEventPublisher implements AsyncTaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopAsyncTaskEventPublisher.class);

    @Override
    public void publish(AsyncTaskEvent<? extends AsyncTaskPayload> event) {
        log.debug("async task publisher disabled, skip event type={}, resourceId={}", event.type(), event.resourceId());
    }
}
