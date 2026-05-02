package com.example.urbanagent.query.application.dto;

import jakarta.validation.constraints.NotBlank;

public record PreviewQueryRequest(@NotBlank String question) {
}
