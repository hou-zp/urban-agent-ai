package com.example.urbanagent.risk.application;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import com.example.urbanagent.risk.application.dto.LegalReviewActionRequest;
import com.example.urbanagent.risk.application.dto.LegalReviewView;
import com.example.urbanagent.risk.domain.LegalReview;
import com.example.urbanagent.risk.domain.LegalReviewStatus;
import com.example.urbanagent.risk.repository.LegalReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LegalReviewApplicationService {

    private final LegalReviewRepository legalReviewRepository;

    public LegalReviewApplicationService(LegalReviewRepository legalReviewRepository) {
        this.legalReviewRepository = legalReviewRepository;
    }

    @Transactional(readOnly = true)
    public List<LegalReviewView> list(String status) {
        assertLegalReviewer();
        List<LegalReview> reviews;
        if (status == null || status.isBlank()) {
            reviews = legalReviewRepository.findTop20ByOrderByCreatedAtDesc();
        } else {
            reviews = legalReviewRepository.findTop20ByStatusOrderByCreatedAtDesc(parseStatus(status));
        }
        return reviews.stream().map(LegalReviewView::from).toList();
    }

    @Transactional(readOnly = true)
    public LegalReviewView get(String reviewId) {
        assertLegalReviewer();
        return LegalReviewView.from(loadReview(reviewId));
    }

    @Transactional
    public LegalReviewView approve(String reviewId, LegalReviewActionRequest request) {
        assertLegalReviewer();
        LegalReview review = loadReview(reviewId);
        assertPending(review);
        review.approve(UserContextHolder.get().userId(), request.comment(), request.reviewedAnswer());
        return LegalReviewView.from(legalReviewRepository.save(review));
    }

    @Transactional
    public LegalReviewView reject(String reviewId, LegalReviewActionRequest request) {
        assertLegalReviewer();
        LegalReview review = loadReview(reviewId);
        assertPending(review);
        review.reject(UserContextHolder.get().userId(), request.comment());
        return LegalReviewView.from(legalReviewRepository.save(review));
    }

    @Transactional
    public LegalReviewView revise(String reviewId, LegalReviewActionRequest request) {
        assertLegalReviewer();
        LegalReview review = loadReview(reviewId);
        assertPending(review);
        review.revise(UserContextHolder.get().userId(), request.comment(), request.reviewedAnswer());
        return LegalReviewView.from(legalReviewRepository.save(review));
    }

    @Transactional
    public LegalReviewView requestMoreFacts(String reviewId, LegalReviewActionRequest request) {
        assertLegalReviewer();
        LegalReview review = loadReview(reviewId);
        assertPending(review);
        review.requestMoreFacts(UserContextHolder.get().userId(), request.comment());
        return LegalReviewView.from(legalReviewRepository.save(review));
    }

    private void assertLegalReviewer() {
        UserContext userContext = UserContextHolder.get();
        if ("LEGAL".equalsIgnoreCase(userContext.role()) || "ADMIN".equalsIgnoreCase(userContext.role())) {
            return;
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED, "仅法制审核人员或管理员可操作法制审核");
    }

    private void assertPending(LegalReview review) {
        if (review.getStatus() == LegalReviewStatus.PENDING) {
            return;
        }
        throw new BusinessException(ErrorCode.LEGAL_REVIEW_STATUS_INVALID, "当前审核记录已处理，不能重复操作");
    }

    private LegalReview loadReview(String reviewId) {
        return legalReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_REVIEW_NOT_FOUND));
    }

    private LegalReviewStatus parseStatus(String status) {
        try {
            return LegalReviewStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "unknown legal review status");
        }
    }
}
