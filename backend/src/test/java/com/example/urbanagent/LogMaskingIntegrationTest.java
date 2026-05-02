package com.example.urbanagent;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingIntegrationTest {

    @Test
    void shouldConfigureSensitiveMaskingDecorator() throws Exception {
        ClassPathResource resource = new ClassPathResource("logback-spring.xml");
        String xml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(xml).contains("SensitiveLogMaskingJsonGeneratorDecorator");
        assertThat(xml).contains("jsonGeneratorDecorator");
    }
}
