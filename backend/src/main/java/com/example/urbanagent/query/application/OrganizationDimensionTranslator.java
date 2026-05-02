package com.example.urbanagent.query.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrganizationDimensionTranslator {

    private final JdbcTemplate jdbcTemplate;

    public OrganizationDimensionTranslator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> translate(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<String> streetCodes = collectCodes(rows, "street_code");
        if (streetCodes.isEmpty()) {
            return rows;
        }
        Map<String, String> streetNames = loadOrganizationNames(streetCodes);
        if (streetNames.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(row -> translateStreetCode(row, streetNames))
                .toList();
    }

    private Set<String> collectCodes(List<Map<String, Object>> rows, String fieldName) {
        Set<String> codes = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = readValue(row, fieldName);
            if (value != null && !String.valueOf(value).isBlank()) {
                codes.add(String.valueOf(value));
            }
        }
        return codes;
    }

    private Map<String, String> loadOrganizationNames(Set<String> orgCodes) {
        String placeholders = orgCodes.stream().map(ignored -> "?").collect(Collectors.joining(","));
        String sql = "select org_code, org_name from dim_organization where enabled = true and org_code in (" + placeholders + ")";
        List<Object> args = new ArrayList<>(orgCodes);
        return jdbcTemplate.query(sql, args.toArray(), resultSet -> {
            Map<String, String> names = new LinkedHashMap<>();
            while (resultSet.next()) {
                names.put(resultSet.getString("org_code"), resultSet.getString("org_name"));
            }
            return names;
        });
    }

    private Map<String, Object> translateStreetCode(Map<String, Object> row, Map<String, String> streetNames) {
        Map<String, Object> translated = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (isField(entry.getKey(), "street_code")) {
                String code = String.valueOf(entry.getValue());
                translated.put("STREET_NAME", streetNames.getOrDefault(code, code));
                continue;
            }
            translated.put(entry.getKey(), entry.getValue());
        }
        return translated;
    }

    private Object readValue(Map<String, Object> row, String fieldName) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (isField(entry.getKey(), fieldName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isField(String actual, String expected) {
        return actual != null && actual.toLowerCase(Locale.ROOT).equals(expected);
    }
}
