package com.example.urbanagent.query.repository;

import com.example.urbanagent.query.domain.AiDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiDataSourceRepository extends JpaRepository<AiDataSource, String> {

    Optional<AiDataSource> findByDataSourceCodeAndEnabledTrue(String dataSourceCode);

    Optional<AiDataSource> findFirstByEnabledTrueOrderByCreatedAtAsc();
}