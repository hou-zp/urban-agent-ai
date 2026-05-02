package com.example.urbanagent.agent.repository;

import com.example.urbanagent.agent.domain.QuestionParseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionParseRecordRepository extends JpaRepository<QuestionParseRecord, String> {
}
