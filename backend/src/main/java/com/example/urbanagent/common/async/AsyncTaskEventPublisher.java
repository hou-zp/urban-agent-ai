package com.example.urbanagent.common.async;

public interface AsyncTaskEventPublisher {

    void publish(AsyncTaskEvent<? extends AsyncTaskPayload> event);
}
