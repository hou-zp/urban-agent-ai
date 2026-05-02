package com.example.urbanagent.query.repository;

import com.example.urbanagent.query.domain.QueryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueryRecordRepository extends JpaRepository<QueryRecord, String> {

    List<QueryRecord> findTop20ByOrderByCreatedAtDesc();
}
