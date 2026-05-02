package com.example.urbanagent.agent.repository;

import com.example.urbanagent.agent.domain.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentSessionRepository extends JpaRepository<AgentSession, String> {

    List<AgentSession> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
}
