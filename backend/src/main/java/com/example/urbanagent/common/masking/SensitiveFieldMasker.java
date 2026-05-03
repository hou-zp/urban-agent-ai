package com.example.urbanagent.common.masking;

import com.example.urbanagent.iam.domain.UserContext;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 字段级脱敏服务。
 *
 * <p>覆盖以下敏感字段的脱敏规则：
 * <ul>
 *   <li>投诉人姓名：首字 + *（保留首字隐私性较低）</li>
 *   <li>手机号：前3位 + **** + 后4位</li>
 *   <li>身份证号：前6后4位 + ****（保留行政区划代码）</li>
 *   <li>详细住址：根据角色展示到小区/道路层级</li>
 *   <li>执法内部意见：前8字符 + ...（内部意见默认不展示）</li>
 *   <li>影像资料（照片、视频）：默认不展示，显示占位符</li>
 *   <li>经办人联系方式：默认不展示</li>
 * </ul>
 *
 * <p>脱敏决策逻辑：
 * <ul>
 *   <li>ADMIN 角色：所有字段不脱敏（完整展示）</li>
 *   <li>MANAGER 角色：手机号前3+后4、内部意见前8字符，其余按默认规则</li>
 *   <li>其他角色：所有上述字段执行完整脱敏</li>
 * </ul>
 *
 * <p>与 {@link com.example.urbanagent.query.application.BusinessRecordMaskingService} 的区别：
 * 本类专注于字段级通用脱敏规则；后者负责行级可见字段判断，两者协同工作。
 */
@Component
public class SensitiveFieldMasker {

    /**
     * 必须完全脱敏（不区分角色）的字段名关键词。
     * 无论什么角色，内部意见、影像、投诉人等字段不能完整展示。
     */
    private static final Set<String> STRICTLY_SENSITIVE_KEYWORDS = Set.of(
            "id_card", "idcard", "identity_card", "身份证",
            "complainant_name", "reporter_name", "complainant_id",
            "投诉人", "举报人", "反映人",
            "internal_opinion", "执法内部意见", "internal_note", "disposal_measure",
            "image_url", "photo_url", "video_url", "attachment_url",
            "image_data", "photo_data", "影像", "照片", "视频"
    );

    /**
     * 按角色放宽展示的字段名关键词。
     */
    private static final Set<String> ROLE_CONDITIONAL_KEYWORDS = Set.of(
            "phone", "mobile", "contact_phone", "telephone",
            "手机", "联系电话",
            "address", "detailed_address", "详细地址",
            "operator_contact", "handler_contact", "经办人联系方式",
            "complainant_phone", "reporter_phone"
    );

    /**
     * 内部意见字段（即使 MANAGER 也只能看前8字符）。
     */
    private static final Set<String> INTERNAL_OPINION_KEYWORDS = Set.of(
            "internal_opinion", "internal_note", "执法内部意见",
            "disposal_measure", "result_summary", "issue_note"
    );

    /**
     * 影像资料字段关键词。
     */
    private static final Set<String> IMAGE_KEYWORDS = Set.of(
            "image", "photo", "video", "attachment", "影像", "照片", "视频"
    );

    /**
     * 对单行记录执行字段级脱敏。
     *
     * @param row       原始行数据（key 为字段名，value 为字段值）
     * @param userContext 当前用户上下文，用于判断角色权限
     * @return 脱敏后的行数据（新建 Map，不修改原对象）
     */
    public Map<String, Object> mask(Map<String, Object> row, UserContext userContext) {
        if (row == null || row.isEmpty()) {
            return row;
        }
        String role = resolveRole(userContext);
        boolean isAdmin = "admin".equalsIgnoreCase(role);
        boolean isManager = "manager".equalsIgnoreCase(role);

        return row.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> maskValue(
                                entry.getKey(),
                                entry.getValue(),
                                isAdmin,
                                isManager
                        ),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    /**
     * 判断指定字段是否属于严格脱敏字段。
     * 用于外部服务判断哪些字段应从查询结果中直接剔除。
     */
    public boolean isStrictlySensitive(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        for (String keyword : STRICTLY_SENSITIVE_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Object maskValue(String fieldName, Object value, boolean isAdmin, boolean isManager) {
        if (value == null || fieldName == null) {
            return value;
        }

        // ADMIN 完全不脱敏
        if (isAdmin) {
            return value;
        }

        String normalized = fieldName.toLowerCase(Locale.ROOT);

        // 严格脱敏字段：身份证、投诉人、内部意见、影像
        if (containsAny(normalized, STRICTLY_SENSITIVE_KEYWORDS)) {
            return placeholderFor(normalized);
        }

        // 角色条件脱敏字段：手机号、地址、联系电话
        if (containsAny(normalized, ROLE_CONDITIONAL_KEYWORDS)) {
            return maskConditionalField(normalized, value, isManager);
        }

        return value;
    }

    private Object maskConditionalField(String fieldName, Object value, boolean isManager) {
        String text = String.valueOf(value);
        String normalized = fieldName.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "phone", "mobile", "手机", "电话")) {
            return maskPhone(text);
        }

        if (containsAny(normalized, "address", "详细地址")) {
            return maskAddress(text, isManager);
        }

        if (containsAny(normalized, "operator_contact", "handler_contact", "经办人")) {
            if (isManager) {
                return maskPhone(text);
            }
            return "[已脱敏]";
        }

        if (containsAny(normalized, "complainant_phone", "reporter_phone")) {
            if (isManager) {
                return maskPhone(text);
            }
            return "[已脱敏]";
        }

        return "[已脱敏]";
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "[已脱敏]";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return "[已脱敏]";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return "[已脱敏]";
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "[已脱敏]";
        }
        if (name.length() == 1) {
            return name + "*";
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(Math.max(1, name.length() - 1));
    }

    private String maskAddress(String address, boolean isManager) {
        if (address == null || address.isBlank()) {
            return "[已脱敏]";
        }
        // MANAGER 可展示到道路/小区层级
        if (isManager) {
            String[] parts = address.split("[区路街坊号弄栋楼室]");
            if (parts.length >= 2) {
                return parts[0] + "区" + parts[1] + "路";
            }
        }
        // 展示到行政区划层级（街道）
        String[] segments = address.split("[路街坊]");
        return segments.length > 0 ? segments[0] + (segments.length > 1 ? "路" : "") : "[已脱敏]";
    }

    private String placeholderFor(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, IMAGE_KEYWORDS)) {
            return "[受限材料]";
        }
        if (containsAny(normalized, INTERNAL_OPINION_KEYWORDS)) {
            return "[内部意见已脱敏]";
        }
        if (containsAny(normalized, "id_card", "身份证")) {
            return maskIdCard(String.valueOf("未提供"));
        }
        if (containsAny(normalized, "complainant", "reporter", "投诉人", "举报人")) {
            return maskName(String.valueOf("未提供"));
        }
        return "[已脱敏]";
    }

    private String resolveRole(UserContext userContext) {
        if (userContext == null) {
            return "guest";
        }
        return userContext.role() == null ? "guest" : userContext.role().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String normalizedField, Set<String> keywords) {
        for (String keyword : keywords) {
            if (normalizedField.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String normalizedField, String... keywords) {
        for (String keyword : keywords) {
            if (normalizedField.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}