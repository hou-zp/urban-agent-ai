package com.example.urbanagent.risk.repository;

import com.example.urbanagent.risk.domain.RiskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskEventRepository extends JpaRepository<RiskEvent, String> {

    List<RiskEvent> findTop20ByOrderByCreatedAtDesc();
}
