package com.example.urbanagent.agent.repository;

import com.example.urbanagent.agent.domain.ToolCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolCallRepository extends JpaRepository<ToolCall, String> {

    List<ToolCall> findTop20ByOrderByCreatedAtDesc();

    List<ToolCall> findByRunIdAndToolNameOrderByCreatedAtDesc(String runId, String toolName);

    List<ToolCall> findByRunIdAndToolNameInOrderByCreatedAtAsc(String runId, List<String> toolNames);
}
