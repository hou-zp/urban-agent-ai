package com.example.urbanagent.common.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "urban-agent.async", name = "publisher", havingValue = "amqp")
public class AmqpAsyncTaskEventPublisher implements AsyncTaskEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public AmqpAsyncTaskEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
    }

    @Override
    public void publish(AsyncTaskEvent<? extends AsyncTaskPayload> event) {
        rabbitTemplate.convertAndSend(event.exchange(), event.routingKey(), event);
    }
}
