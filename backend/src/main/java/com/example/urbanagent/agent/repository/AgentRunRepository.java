package com.example.urbanagent.agent.repository;

import com.example.urbanagent.agent.domain.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {

    List<AgentRun> findTop20ByOrderByCreatedAtDesc();

    Optional<AgentRun> findTopBySessionIdOrderByCreatedAtDesc(String sessionId);
}
