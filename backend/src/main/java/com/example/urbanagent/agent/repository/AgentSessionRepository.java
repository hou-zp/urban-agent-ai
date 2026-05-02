package com.example.urbanagent.agent.repository;

import com.example.urbanagent.agent.domain.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentSessionRepository extends JpaRepository<AgentSession, String> {

    List<AgentSession> findTop20ByUserIdOrderByCreatedAtDesc(String userId);

    Optional<AgentSession> findByIdAndUserId(String id, String userId);

    boolean existsByIdAndUserId(String id, String userId);
}
