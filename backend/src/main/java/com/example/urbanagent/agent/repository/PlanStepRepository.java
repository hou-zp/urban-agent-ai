package com.example.urbanagent.agent.repository;

import com.example.urbanagent.agent.domain.PlanStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanStepRepository extends JpaRepository<PlanStep, String> {

    List<PlanStep> findByPlanIdOrderByStepOrderAsc(String planId);
}
