package com.example.urbanagent.common.async;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(prefix = "urban-agent.async", name = "publisher", havingValue = "memory")
public class InMemoryAsyncTaskEventPublisher implements AsyncTaskEventPublisher {

    private final CopyOnWriteArrayList<AsyncTaskEvent<? extends AsyncTaskPayload>> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(AsyncTaskEvent<? extends AsyncTaskPayload> event) {
        events.add(event);
    }

    public List<AsyncTaskEvent<? extends AsyncTaskPayload>> publishedEvents() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
