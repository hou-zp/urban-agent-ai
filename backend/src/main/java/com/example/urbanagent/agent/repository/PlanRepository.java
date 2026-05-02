package com.example.urbanagent.agent.repository;

import com.example.urbanagent.agent.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, String> {

    Optional<Plan> findByRunId(String runId);
}
