package com.example.urbanagent.common.logging;

import com.fasterxml.jackson.core.JsonStreamContext;
import net.logstash.logback.mask.ValueMasker;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 日志敏感信息脱敏器。
 * 自动脱敏：手机号、身份证号、API Key、内部意见、执法材料等。
 */
public class SensitiveLogValueMasker implements ValueMasker {

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            // 手机号脱敏：138****1234
            new Rule(Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})"), "$1****$2"),
            // 身份证号脱敏：前6后4保留，中间脱敏
            new Rule(Pattern.compile("([1-9]\\d{5})\\d{8}(\\d{4})"), "$1********$2"),
            // API Key / Secret Key
            new Rule(Pattern.compile("(?i)\\bsk-[a-z0-9_-]{10,}\\b"), "[masked]"),
            new Rule(Pattern.compile("(?i)(api[-_ ]?key\\s*[:=]\\s*)([^,，;；\\s\"}]+)"), "$1[masked]"),
            new Rule(Pattern.compile("(?i)(secret[-_ ]?key\\s*[:=]\\s*)([^,，;；\\s\"}]+)"), "$1[masked]"),
            new Rule(Pattern.compile("(?i)(access[-_ ]?key\\s*[:=]\\s*)([^,，;；\\s\"}]+)"), "$1[masked]"),
            // Authorization / Token
            new Rule(Pattern.compile("(?i)(authorization\\s*[:=]\\s*)([^,，;；\\s\"}]+)"), "$1[masked]"),
            new Rule(Pattern.compile("(?i)(token\\s*[:=]\\s*)([^,，;；\\s\"}]+)"), "$1[masked]"),
            // 内部意见脱敏
            new Rule(Pattern.compile("(?i)(issue_note\\s*[:=]\\s*)([^,，}\\]]+)"), "$1[已脱敏]"),
            new Rule(Pattern.compile("(内部意见[^\\n\\r]{0,8}[:：]\\s*)([^\\n\\r，,]+)"), "$1[已脱敏]"),
            // 执法材料脱敏
            new Rule(Pattern.compile("(complainant[^\\n\\r]{0,10}[:：]\\s*)([^\\n\\r，,]+)"), "$1[已脱敏]"),
            new Rule(Pattern.compile("(投诉人[^\\n\\r]{0,4}[:：]\\s*)([^\\n\\r，,]+)"), "$1[已脱敏]"),
            // 地址脱敏（只保留到街道级别）
            new Rule(Pattern.compile("(详细地址[^\\n\\r]{0,6}[:：]\\s*)([^\\n\\r]{10,})"), "$1***")
    );

    @Override
    public Object mask(JsonStreamContext context, Object value) {
        if (!(value instanceof CharSequence text)) {
            return null;
        }
        String masked = text.toString();
        for (Rule rule : RULES) {
            masked = rule.pattern().matcher(masked).replaceAll(rule.replacement());
        }
        return masked.equals(text.toString()) ? null : masked;
    }
}
