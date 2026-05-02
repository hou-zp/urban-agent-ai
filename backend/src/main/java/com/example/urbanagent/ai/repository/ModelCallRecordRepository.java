package com.example.urbanagent.ai.repository;

import com.example.urbanagent.ai.domain.ModelCallRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelCallRecordRepository extends JpaRepository<ModelCallRecord, String> {

    List<ModelCallRecord> findTop20ByOrderByCreatedAtDesc();
}
