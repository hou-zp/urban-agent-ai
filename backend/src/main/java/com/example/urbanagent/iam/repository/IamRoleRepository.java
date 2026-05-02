package com.example.urbanagent.iam.repository;

import com.example.urbanagent.iam.domain.IamRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamRoleRepository extends JpaRepository<IamRole, String> {

    boolean existsByRoleCodeAndEnabledTrue(String roleCode);
}
