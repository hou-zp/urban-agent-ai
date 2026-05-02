package com.example.urbanagent.iam.repository;

import com.example.urbanagent.iam.domain.IamUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IamUserRepository extends JpaRepository<IamUser, String> {

    Optional<IamUser> findByIdAndEnabledTrue(String id);
}
