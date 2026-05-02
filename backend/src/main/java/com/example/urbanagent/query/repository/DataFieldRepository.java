package com.example.urbanagent.query.repository;

import com.example.urbanagent.query.domain.DataField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DataFieldRepository extends JpaRepository<DataField, String> {

    List<DataField> findByTableIdIn(Collection<String> tableIds);
}
