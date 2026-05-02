package com.example.urbanagent.query.application;

import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.query.domain.DataField;
import com.example.urbanagent.query.domain.DataTable;
import com.example.urbanagent.query.domain.SensitiveLevel;
import com.example.urbanagent.query.repository.DataFieldRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QueryAccessPolicyService {

    private final DataFieldRepository dataFieldRepository;

    public QueryAccessPolicyService(DataFieldRepository dataFieldRepository) {
        this.dataFieldRepository = dataFieldRepository;
    }

    public boolean canAccessRegion(String regionCode, UserContext userContext) {
        if (regionCode == null || regionCode.isBlank() || "city".equalsIgnoreCase(regionCode)) {
            return true;
        }
        if (isAdmin(userContext)) {
            return true;
        }
        return regionCode.equalsIgnoreCase(userContext.region());
    }

    public boolean canAccessField(DataField field, UserContext userContext) {
        if (isAdmin(userContext)) {
            return true;
        }
        if (field.getSensitiveLevel() == SensitiveLevel.RESTRICTED) {
            return false;
        }
        for (String role : field.getVisibleRoles().split(",")) {
            if (role.trim().equalsIgnoreCase(userContext.role())) {
                return true;
            }
        }
        return false;
    }

    public Set<String> visibleFieldNames(String tableId, UserContext userContext) {
        return fieldsByTableId(tableId).stream()
                .filter(field -> canAccessField(field, userContext))
                .map(field -> normalize(field.getFieldName()))
                .collect(Collectors.toSet());
    }

    public Set<String> allFieldNames(String tableId) {
        return fieldsByTableId(tableId).stream()
                .map(field -> normalize(field.getFieldName()))
                .collect(Collectors.toSet());
    }

    public Set<String> dateFieldNames(String tableId) {
        return fieldsByTableId(tableId).stream()
                .filter(field -> "date".equalsIgnoreCase(field.getDataType()))
                .map(field -> normalize(field.getFieldName()))
                .collect(Collectors.toSet());
    }

    private List<DataField> fieldsByTableId(String tableId) {
        return dataFieldRepository.findByTableIdIn(List.of(tableId));
    }

    private boolean isAdmin(UserContext userContext) {
        return "ADMIN".equalsIgnoreCase(userContext.role());
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
