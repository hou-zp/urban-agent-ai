package com.example.urbanagent.iam.repository;

import com.example.urbanagent.iam.domain.IamRegion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamRegionRepository extends JpaRepository<IamRegion, String> {

    boolean existsByRegionCodeAndEnabledTrue(String regionCode);
}
