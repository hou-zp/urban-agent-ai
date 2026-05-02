package com.example.urbanagent.risk.repository;

import com.example.urbanagent.risk.domain.LegalReview;
import com.example.urbanagent.risk.domain.LegalReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalReviewRepository extends JpaRepository<LegalReview, String> {

    List<LegalReview> findTop20ByOrderByCreatedAtDesc();

    List<LegalReview> findTop20ByStatusOrderByCreatedAtDesc(LegalReviewStatus status);
}
