package com.example.urbanagent.common.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志敏感信息脱敏测试。
 */
class SensitiveLogValueMaskerTest {

    private final SensitiveLogValueMasker masker = new SensitiveLogValueMasker();

    @Test
    void shouldMaskPhoneNumber() {
        String masked = (String) masker.mask(null, "13812345678");
        assertEquals("138****5678", masked);
    }

    @Test
    void shouldMaskIdCardNumber() {
        String masked = (String) masker.mask(null, "310101199001011234");
        // 身份证脱敏结果包含星号即可
        assertTrue(masked.contains("*"), "应该脱敏身份证: " + masked);
    }

    @Test
    void shouldMaskApiKey() {
        String masked = (String) masker.mask(null, "sk-1234567890abcdefghij");
        assertEquals("[masked]", masked);
    }

    @Test
    void shouldMaskApiKeyWithLabel() {
        String masked = (String) masker.mask(null, "api_key=sk-1234567890");
        assertEquals("api_key=[masked]", masked);
    }

    @Test
    void shouldMaskSecretKey() {
        String masked = (String) masker.mask(null, "secret_key=mysecret");
        assertEquals("secret_key=[masked]", masked);
    }

    @Test
    void shouldMaskToken() {
        String masked = (String) masker.mask(null, "token=eyJhbGciOiJIUzI1NiJ9");
        assertEquals("token=[masked]", masked);
    }

    @Test
    void shouldMaskInternalOpinion() {
        String masked = (String) masker.mask(null, "内部意见：建议按简易程序处理");
        // 脱敏后包含[已脱敏]
        assertTrue(masked.contains("[已脱敏]"), "应该脱敏内部意见: " + masked);
    }

    @Test
    void shouldMaskComplainantInfo() {
        String masked = (String) masker.mask(null, "投诉人：张三，联系电话：13812345678");
        assertTrue(masked.contains("[已脱敏]"));
        assertTrue(masked.contains("138****"));
    }

    @Test
    void shouldReturnNullForNonSensitiveContent() {
        Object result = masker.mask(null, "这是一条普通日志内容");
        assertNull(result);
    }

    @Test
    void shouldReturnNullForNumericContent() {
        Object result = masker.mask(null, "12345");
        assertNull(result);
    }

    @Test
    void shouldHandleNullValue() {
        Object result = masker.mask(null, null);
        assertNull(result);
    }

    @Test
    void shouldMaskMultipleSensitiveFields() {
        String masked = (String) masker.mask(null, "手机号：13812345678，身份证：310101199001011234");
        assertTrue(masked.contains("138****"), "应该脱敏手机号: " + masked);
        assertTrue(masked.contains("********") || masked.matches(".+\\*+.*"), "应该脱敏身份证: " + masked);
    }

    @Test
    void shouldMaskAuthorizationHeader() {
        String masked = (String) masker.mask(null, "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9");
        // Bearer token 应该被脱敏
        assertTrue(masked.contains("[masked]"), "应该脱敏 Authorization: " + masked);
    }
}