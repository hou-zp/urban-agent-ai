package com.example.urbanagent.agent.application.dto;

public record ExtractedSlot(
        SlotType slotType,
        String rawValue,
        String normalizedValue,
        boolean mandatory,
        double confidence
) {
}
