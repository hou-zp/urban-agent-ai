package com.example.urbanagent.common.config;

import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@Configuration
public class AsyncConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("urban-agent-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setTaskDecorator(runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            UserContext userContext = UserContextHolder.currentOrNull();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                UserContext previousUserContext = UserContextHolder.currentOrNull();
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    } else {
                        MDC.clear();
                    }
                    if (userContext != null) {
                        UserContextHolder.set(userContext);
                    } else {
                        UserContextHolder.clear();
                    }
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                    if (previousUserContext != null) {
                        UserContextHolder.set(previousUserContext);
                    } else {
                        UserContextHolder.clear();
                    }
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
