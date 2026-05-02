package com.example.urbanagent.knowledge.application.dto;

import com.example.urbanagent.knowledge.domain.KnowledgeDocumentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateKnowledgeStatusRequest(@NotNull KnowledgeDocumentStatus status) {
}
