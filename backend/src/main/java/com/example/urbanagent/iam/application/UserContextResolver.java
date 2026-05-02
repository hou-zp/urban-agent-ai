package com.example.urbanagent.iam.application;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.iam.domain.IamUser;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.repository.IamRegionRepository;
import com.example.urbanagent.iam.repository.IamRoleRepository;
import com.example.urbanagent.iam.repository.IamUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class UserContextResolver {

    private static final String DEFAULT_USER_ID = "demo-user";

    private final IamUserRepository iamUserRepository;
    private final IamRoleRepository iamRoleRepository;
    private final IamRegionRepository iamRegionRepository;

    public UserContextResolver(IamUserRepository iamUserRepository,
                               IamRoleRepository iamRoleRepository,
                               IamRegionRepository iamRegionRepository) {
        this.iamUserRepository = iamUserRepository;
        this.iamRoleRepository = iamRoleRepository;
        this.iamRegionRepository = iamRegionRepository;
    }

    @Transactional(readOnly = true)
    public UserContext resolve(String userIdHeader, String roleHeader, String regionHeader) {
        String userId = valueOrDefault(userIdHeader, DEFAULT_USER_ID);
        Optional<IamUser> user = iamUserRepository.findByIdAndEnabledTrue(userId);

        String role = normalizeRole(roleHeader);
        String region = normalizeRegion(regionHeader);
        if (user.isPresent()) {
            IamUser iamUser = user.get();
            role = role == null ? normalizeRole(iamUser.getRoleCode()) : role;
            region = region == null ? normalizeRegion(iamUser.getRegionCode()) : region;
        } else if (role == null || region == null) {
            throw new BusinessException(ErrorCode.USER_CONTEXT_INVALID, "用户不存在或缺少角色、区域信息");
        }

        validateRole(role);
        validateRegion(region);
        return new UserContext(userId, role, region);
    }

    private void validateRole(String role) {
        if (!iamRoleRepository.existsByRoleCodeAndEnabledTrue(role)) {
            throw new BusinessException(ErrorCode.USER_CONTEXT_INVALID, "角色不存在或已停用: " + role);
        }
    }

    private void validateRegion(String region) {
        if (!iamRegionRepository.existsByRegionCodeAndEnabledTrue(region)) {
            throw new BusinessException(ErrorCode.USER_CONTEXT_INVALID, "区域不存在或已停用: " + region);
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRegion(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        return region.trim().toLowerCase(Locale.ROOT);
    }
}
