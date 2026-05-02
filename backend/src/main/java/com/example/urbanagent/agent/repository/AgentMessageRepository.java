package com.example.urbanagent.agent.repository;

import com.example.urbanagent.agent.domain.AgentMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, String> {

    List<AgentMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
