package com.example.urbanagent.common.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板加载服务。
 *
 * <p>从 {@code resources/prompts/} 目录加载模板文件，支持：
 * <ul>
 *   <li>意图识别模板（intent-analysis.txt）</li>
 *   <li>答案融合模板（answer-composition.txt）</li>
 *   <li>安全闸门模板（guardrail.txt）</li>
 * </ul>
 *
 * <p>模板内容缓存在内存中（ConcurrentHashMap），避免每次请求重复读取文件。
 * 生产环境可通过 {@link #reload()} 实现热更新。
 *
 * <p>与硬编码提示词的区别：
 * 本服务将提示词作为独立资源文件管理，便于非开发人员维护和 A/B 调优；
 * Spring 的 ClassPathResource 确保模板随 JAR 包部署。
 */
@Service
public class PromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);

    private static final String PROMPTS_PATH = "prompts/";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final Map<String, String> templatePaths = Map.of(
            "intent-analysis", "intent-analysis.txt",
            "answer-composition", "answer-composition.txt",
            "guardrail", "guardrail.txt"
    );

    /**
     * 获取指定名称的提示词模板。
     *
     * @param templateName 模板标识（intent-analysis / answer-composition / guardrail）
     * @return 模板内容，若加载失败返回 null
     */
    public String getTemplate(String templateName) {
        return cache.computeIfAbsent(templateName, this::loadFromClassPath);
    }

    /**
     * 重新加载所有模板（热更新）。
     */
    public void reload() {
        cache.clear();
        for (String name : templatePaths.keySet()) {
            loadFromClassPath(name);
        }
        log.info("Prompt templates reloaded. templates={}", cache.keySet());
    }

    /**
     * 检查指定模板是否存在且已成功加载。
     */
    public boolean isLoaded(String templateName) {
        return cache.containsKey(templateName) && cache.get(templateName) != null;
    }

    private String loadFromClassPath(String templateName) {
        String fileName = templatePaths.get(templateName);
        if (fileName == null) {
            log.warn("Unknown template name: {}", templateName);
            return null;
        }
        try {
            ClassPathResource resource = new ClassPathResource(PROMPTS_PATH + fileName);
            if (!resource.exists()) {
                log.warn("Prompt template file not found: {}{}", PROMPTS_PATH, fileName);
                return null;
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Loaded prompt template: {} ({} chars)", templateName, content.length());
            return content;
        } catch (IOException ex) {
            log.error("Failed to load prompt template: {}{}", PROMPTS_PATH, fileName, ex);
            return null;
        }
    }
}