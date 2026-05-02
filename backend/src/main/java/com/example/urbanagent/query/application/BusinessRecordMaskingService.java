package com.example.urbanagent.query.application;

import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.query.domain.DataField;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusinessRecordMaskingService {

    public Map<String, Object> apply(UserContext userContext,
                                     Map<String, Object> row,
                                     List<DataField> visibleFields) {
        Map<String, DataField> fieldMap = visibleFields.stream()
                .collect(Collectors.toMap(
                        field -> normalize(field.getFieldName()),
                        field -> field
                ));
        return row.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> maskValue(userContext, fieldMap.get(normalize(entry.getKey())), entry.getValue()),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    public Set<String> maskedFields(UserContext userContext, List<DataField> visibleFields) {
        return visibleFields.stream()
                .filter(field -> shouldMask(userContext, field))
                .map(DataField::getFieldName)
                .collect(Collectors.toSet());
    }

    private Object maskValue(UserContext userContext, DataField field, Object value) {
        if (value == null || field == null || !shouldMask(userContext, field)) {
            return value;
        }
        String normalizedFieldName = normalize(field.getFieldName());
        String text = String.valueOf(value);
        if (normalizedFieldName.contains("phone")) {
            return maskPhone(text);
        }
        if (normalizedFieldName.contains("contact_name") || normalizedFieldName.contains("contact")) {
            return maskName(text);
        }
        if (normalizedFieldName.contains("issue_note")
                || normalizedFieldName.contains("result_summary")
                || normalizedFieldName.contains("disposal_measure")) {
            return maskInternalOpinion(text);
        }
        if (containsAny(normalizedFieldName, "image", "photo", "video", "attachment")) {
            return "[受限材料]";
        }
        return "[已脱敏]";
    }

    private boolean shouldMask(UserContext userContext, DataField field) {
        if (userContext == null || field == null) {
            return false;
        }
        String role = normalize(userContext.role());
        if ("admin".equals(role)) {
            return false;
        }
        String fieldName = normalize(field.getFieldName());
        return "manager".equals(role) && (
                fieldName.contains("phone")
                        || fieldName.contains("contact_name")
                        || fieldName.contains("contact")
                        || fieldName.contains("issue_note")
                        || fieldName.contains("result_summary")
                        || fieldName.contains("disposal_measure")
                        || containsAny(fieldName, "image", "photo", "video", "attachment")
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "[已脱敏]";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "[已脱敏]";
        }
        if (name.length() == 1) {
            return name + "*";
        }
        return name.substring(0, 1) + "*".repeat(Math.max(1, name.length() - 1));
    }

    private String maskInternalOpinion(String opinion) {
        if (opinion == null || opinion.isBlank()) {
            return "[已脱敏]";
        }
        int visibleLength = Math.min(8, opinion.length());
        return opinion.substring(0, visibleLength) + "...（内部意见已脱敏）";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
