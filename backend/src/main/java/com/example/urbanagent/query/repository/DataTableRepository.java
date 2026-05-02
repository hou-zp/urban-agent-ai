package com.example.urbanagent.query.repository;

import com.example.urbanagent.query.domain.DataTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DataTableRepository extends JpaRepository<DataTable, String> {

    List<DataTable> findByEnabledTrueOrderByBusinessNameAsc();

    List<DataTable> findByIdIn(Collection<String> ids);

    Optional<DataTable> findByTableNameAndEnabledTrue(String tableName);
}
