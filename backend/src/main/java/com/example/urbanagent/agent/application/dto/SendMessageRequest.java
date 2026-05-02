package com.example.urbanagent.agent.application.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank(message = "content is required")
        String content
) {
}
