package com.example.urbanagent.query.repository;

import com.example.urbanagent.query.domain.MetricDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, String> {

    List<MetricDefinition> findByEnabledTrueOrderByMetricCodeAsc();

    Optional<MetricDefinition> findByMetricCode(String metricCode);
}
