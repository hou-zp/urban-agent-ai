package com.example.urbanagent.query.repository;

import com.example.urbanagent.query.domain.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceRepository extends JpaRepository<DataSource, String> {
}
