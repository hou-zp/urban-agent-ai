package com.example.urbanagent.common.async;

import java.time.Instant;

public record AsyncTaskEvent<T extends AsyncTaskPayload>(String eventId,
                                                         AsyncTaskEventType type,
                                                         String exchange,
                                                         String routingKey,
                                                         String resourceType,
                                                         String resourceId,
                                                         T payload,
                                                         String requestedBy,
                                                         Instant requestedAt,
                                                         String traceId,
                                                         String source) {
}
