package com.example.urbanagent.agent.application;

import com.example.urbanagent.agent.application.dto.PlanStepArtifact;
import com.example.urbanagent.agent.domain.PlanStep;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PlanStepArtifactMapper {

    private final ObjectMapper objectMapper;

    public PlanStepArtifactMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<PlanStepArtifact> read(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PlanStepArtifact.class));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    public Optional<PlanStepArtifact> read(PlanStep step) {
        if (step == null) {
            return Optional.empty();
        }
        return read(step.getOutputPayloadJson());
    }

    public String write(PlanStepArtifact artifact) {
        try {
            return objectMapper.writeValueAsString(artifact);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
