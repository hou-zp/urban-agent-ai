package com.example.urbanagent.ai.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class MockModelProvider implements ModelProvider {

    @Override
    public String name() {
        return "mock-model";
    }

    @Override
    public String chat(List<String> history, String prompt) {
        return buildResponse(history, prompt);
    }

    @Override
    public String structuredChat(List<String> history, String prompt, String jsonSchema) {
        return "{"
                + "\"answer\":\"" + escapeJson(buildResponse(history, prompt)) + "\","
                + "\"confidence\":0.82,"
                + "\"citations\":[],"
                + "\"warnings\":[]"
                + "}";
    }

    @Override
    public Stream<ModelChunk> streamChat(List<String> history, String prompt) {
        String response = buildResponse(history, prompt);
        List<ModelChunk> chunks = new ArrayList<>();
        String[] tokens = response.split(" ");
        for (int i = 0; i < tokens.length; i++) {
            chunks.add(new ModelChunk(tokens[i] + (i == tokens.length - 1 ? "" : " "), false));
        }
        chunks.add(new ModelChunk("", true));
        return chunks.stream();
    }

    private String buildResponse(List<String> history, String prompt) {
        String question = extractQuestion(prompt);
        if (isCapabilityQuestion(question)) {
            return "当前平台支持业务咨询、政策法规检索与解读、线索处置结果查询，以及通过自然语言完成智能问数与结果展示。";
        }

        List<String> references = extractReferences(prompt);
        String prefix = history.isEmpty() ? "已收到您的问题。" : "结合当前会话上下文，";
        if (!references.isEmpty()) {
            StringBuilder builder = new StringBuilder(prefix)
                    .append("根据已检索到的资料，先为您整理出以下关键信息：\n");
            for (int index = 0; index < references.size(); index++) {
                builder.append(index + 1).append(". ").append(references.get(index)).append('\n');
            }
            builder.append("\n如需我继续整理成办理口径、答复意见或要点清单，可以继续补充具体场景。");
            return builder.toString().trim();
        }

        return prefix + "当前还没有检索到可直接引用的依据。我可以继续按业务场景、办理对象或时间范围帮您细化问题，再给出更贴近实务的答复。";
    }

    @Override
    public float[] embed(String content) {
        float[] vector = new float[8];
        for (int index = 0; index < content.length(); index++) {
            vector[index % vector.length] += content.charAt(index);
        }
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / Math.max(1, content.length());
        }
        return vector;
    }

    private String extractQuestion(String prompt) {
        int separatorIndex = prompt.indexOf("\n\n[参考资料]");
        return separatorIndex >= 0 ? prompt.substring(0, separatorIndex) : prompt;
    }

    private List<String> extractReferences(String prompt) {
        int markerIndex = prompt.indexOf("\n\n[参考资料]");
        if (markerIndex < 0) {
            return List.of();
        }

        String referenceBlock = prompt.substring(markerIndex + "\n\n[参考资料]".length()).trim();
        String[] lines = referenceBlock.split("\\R");
        List<String> references = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("请严格依据参考资料回答")) {
                break;
            }
            references.add(trimmed.replaceFirst("^\\d+\\.\\s*", ""));
            if (references.size() >= 3) {
                break;
            }
        }
        return references;
    }

    private boolean isCapabilityQuestion(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("系统能力")
                || normalized.contains("能做什么")
                || normalized.contains("平台能力")
                || normalized.contains("一句话概括");
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
