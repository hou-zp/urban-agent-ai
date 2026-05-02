package com.example.urbanagent.audit.repository;

import com.example.urbanagent.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findTop20ByOrderByCreatedAtDesc();
}
