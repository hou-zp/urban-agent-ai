package com.example.urbanagent.common.logging;

import net.logstash.logback.mask.FieldNameBasedFieldMasker;
import net.logstash.logback.mask.MaskingJsonGeneratorDecorator;

import java.util.Set;

public class SensitiveLogMaskingJsonGeneratorDecorator extends MaskingJsonGeneratorDecorator {

    private static final String DEFAULT_MASK = "[masked]";

    public SensitiveLogMaskingJsonGeneratorDecorator() {
        setDefaultMask(DEFAULT_MASK);
        addFieldMasker(new FieldNameBasedFieldMasker(Set.of(
                "apiKey",
                "secretKey",
                "accessKey",
                "token",
                "authorization",
                "password",
                "reporter_phone",
                "contact_phone",
                "issue_note"
        ), DEFAULT_MASK));
        addValueMasker(new SensitiveLogValueMasker());
    }
}
