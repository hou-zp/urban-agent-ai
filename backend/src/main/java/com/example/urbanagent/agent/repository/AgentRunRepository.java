package com.example.urbanagent.agent.repository;

import com.example.urbanagent.agent.domain.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {

    List<AgentRun> findTop20ByOrderByCreatedAtDesc();

    Optional<AgentRun> findTopBySessionIdOrderByCreatedAtDesc(String sessionId);

    @Query("""
            select agentRun
            from AgentRun agentRun
            join AgentSession agentSession on agentSession.id = agentRun.sessionId
            where agentRun.id = :runId
              and agentSession.userId = :userId
            """)
    Optional<AgentRun> findByIdWithSessionOwner(@Param("runId") String runId, @Param("userId") String userId);
}
