package com.example.urbanagent.query.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ExecuteQueryRequest(
        @NotBlank String question,
        @NotBlank String sql
) {
}
