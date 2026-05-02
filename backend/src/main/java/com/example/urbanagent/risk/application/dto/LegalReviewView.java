package com.example.urbanagent.risk.application.dto;

import com.example.urbanagent.risk.domain.LegalReview;

import java.time.Instant;

public record LegalReviewView(
        String id,
        String riskEventId,
        String runId,
        String sessionId,
        String question,
        String draftAnswer,
        String reviewedAnswer,
        String status,
        String reviewerId,
        String reviewComment,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static LegalReviewView from(LegalReview review) {
        return new LegalReviewView(
                review.getId(),
                review.getRiskEventId(),
                review.getRunId(),
                review.getSessionId(),
                review.getQuestion(),
                review.getDraftAnswer(),
                review.getReviewedAnswer(),
                review.getStatus().name(),
                review.getReviewerId(),
                review.getReviewComment(),
                review.getReviewedAt(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
