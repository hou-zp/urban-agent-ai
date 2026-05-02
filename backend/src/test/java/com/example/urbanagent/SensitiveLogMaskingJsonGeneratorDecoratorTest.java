package com.example.urbanagent;

import com.example.urbanagent.common.logging.SensitiveLogMaskingJsonGeneratorDecorator;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveLogMaskingJsonGeneratorDecoratorTest {

    @Test
    void shouldMaskPhoneSecretAndInternalOpinionValues() throws Exception {
        SensitiveLogMaskingJsonGeneratorDecorator decorator = new SensitiveLogMaskingJsonGeneratorDecorator();
        decorator.start();

        StringWriter writer = new StringWriter();
        JsonGenerator generator = decorator.decorate(new JsonFactory().createGenerator(writer));
        generator.writeStartObject();
        generator.writeStringField("message", "联系人电话 13812345678，apiKey=sk-1234567890abcdef，内部意见: 立即联系商户并记录处置过程");
        generator.writeStringField("secretKey", "secret-value-001");
        generator.writeStringField("contact_phone", "13987654321");
        generator.writeStringField("issue_note", "这是一段完整内部意见文本，不应该原样输出");
        generator.writeEndObject();
        generator.close();

        String json = writer.toString();
        assertThat(json).contains("138****5678");
        assertThat(json).contains("[masked]");
        assertThat(json).contains("内部意见: [已脱敏]");
        assertThat(json).doesNotContain("13987654321");
        assertThat(json).doesNotContain("secret-value-001");
        assertThat(json).doesNotContain("这是一段完整内部意见文本");
    }
}
