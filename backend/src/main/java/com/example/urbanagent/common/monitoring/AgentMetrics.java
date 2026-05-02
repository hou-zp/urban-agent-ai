package com.example.urbanagent.common.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 智能体核心监控指标。
 * 定义和记录系统关键指标。
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        // AI 请求总数
        counter("ai.request.total", "AI 请求总数");
        counter("ai.request.success", "AI 请求成功数");
        counter("ai.request.failed", "AI 请求失败数");
        counter("ai.request.blocked", "AI 请求被拦截数");

        // 模型调用
        counter("ai.model.call.total", "模型调用总数");
        counter("ai.model.call.success", "模型调用成功数");
        counter("ai.model.call.failed", "模型调用失败数");

        // 工具调用
        counter("ai.tool.call.total", "工具调用总数");
        counter("ai.tool.call.success", "工具调用成功数");
        counter("ai.tool.call.failed", "工具调用失败数");

        // 数据问数
        counter("ai.query.preview.total", "问数预览总数");
        counter("ai.query.preview.rejected", "问数预览被拒绝数");
        counter("ai.query.execute.total", "问数执行总数");
        counter("ai.query.execute.rejected", "问数执行被拒绝数");

        // 知识库
        counter("ai.knowledge.search.total", "知识检索总数");
        counter("ai.knowledge.search.hit", "知识检索命中数");
        counter("ai.knowledge.search.miss", "知识检索未命中数");
        counter("ai.knowledge.index.total", "知识索引总数");
        counter("ai.knowledge.index.failed", "知识索引失败数");

        // 风险与法审
        counter("ai.risk.high.count", "高风险次数");
        counter("ai.risk.review.required", "需要法审次数");
        counter("ai.risk.review.pending", "待法审数量");

        // 防造假拦截
        counter("ai.guardrail.blocked", "闸门拦截次数");
        counter("ai.guardrail.data.claim.blocked", "数据声明拦截次数");
        counter("ai.guardrail.law.claim.blocked", "法规声明拦截次数");
        counter("ai.guardrail.policy.claim.blocked", "政策声明拦截次数");

        // 权限
        counter("ai.permission.denied", "权限拒绝次数");

        // 初始化定时器
        timer("ai.request.latency", "AI 请求端到端耗时");
        timer("ai.model.call.latency", "模型调用耗时");
        timer("ai.tool.call.latency", "工具调用耗时");
        timer("ai.query.execute.latency", "问数执行耗时");
    }

    private Counter counter(String name, String description) {
        return counters.computeIfAbsent(name, k ->
                Counter.builder(name)
                        .description(description)
                        .register(registry));
    }

    private Timer timer(String name, String description) {
        return timers.computeIfAbsent(name, k ->
                Timer.builder(name)
                        .description(description)
                        .register(registry));
    }

    // ===== 请求指标 =====

    public void incrementRequestTotal() {
        counters.get("ai.request.total").increment();
    }

    public void incrementRequestSuccess() {
        counters.get("ai.request.success").increment();
    }

    public void incrementRequestFailed() {
        counters.get("ai.request.failed").increment();
    }

    public void incrementRequestBlocked() {
        counters.get("ai.request.blocked").increment();
    }

    // ===== 模型调用指标 =====

    public void incrementModelCallTotal() {
        counters.get("ai.model.call.total").increment();
    }

    public void incrementModelCallSuccess() {
        counters.get("ai.model.call.success").increment();
    }

    public void incrementModelCallFailed() {
        counters.get("ai.model.call.failed").increment();
    }

    // ===== 工具调用指标 =====

    public void incrementToolCallTotal() {
        counters.get("ai.tool.call.total").increment();
    }

    public void incrementToolCallSuccess() {
        counters.get("ai.tool.call.success").increment();
    }

    public void incrementToolCallFailed() {
        counters.get("ai.tool.call.failed").increment();
    }

    // ===== 数据问数指标 =====

    public void incrementQueryPreviewTotal() {
        counters.get("ai.query.preview.total").increment();
    }

    public void incrementQueryPreviewRejected() {
        counters.get("ai.query.preview.rejected").increment();
    }

    public void incrementQueryExecuteTotal() {
        counters.get("ai.query.execute.total").increment();
    }

    public void incrementQueryExecuteRejected() {
        counters.get("ai.query.execute.rejected").increment();
    }

    // ===== 知识库指标 =====

    public void incrementKnowledgeSearchTotal() {
        counters.get("ai.knowledge.search.total").increment();
    }

    public void incrementKnowledgeSearchHit() {
        counters.get("ai.knowledge.search.hit").increment();
    }

    public void incrementKnowledgeSearchMiss() {
        counters.get("ai.knowledge.search.miss").increment();
    }

    public void incrementKnowledgeIndexTotal() {
        counters.get("ai.knowledge.index.total").increment();
    }

    public void incrementKnowledgeIndexFailed() {
        counters.get("ai.knowledge.index.failed").increment();
    }

    // ===== 风险指标 =====

    public void incrementHighRisk() {
        counters.get("ai.risk.high.count").increment();
    }

    public void incrementReviewRequired() {
        counters.get("ai.risk.review.required").increment();
    }

    public void incrementReviewPending() {
        counters.get("ai.risk.review.pending").increment();
    }

    // ===== 防造假拦截指标 =====

    public void incrementGuardrailBlocked() {
        counters.get("ai.guardrail.blocked").increment();
    }

    public void incrementDataClaimBlocked() {
        counters.get("ai.guardrail.data.claim.blocked").increment();
    }

    public void incrementLawClaimBlocked() {
        counters.get("ai.guardrail.law.claim.blocked").increment();
    }

    public void incrementPolicyClaimBlocked() {
        counters.get("ai.guardrail.policy.claim.blocked").increment();
    }

    // ===== 权限指标 =====

    public void incrementPermissionDenied() {
        counters.get("ai.permission.denied").increment();
    }

    // ===== 耗时指标 =====

    public void recordLatency(String name, long durationMs) {
        timers.get(name).record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordLatency(String name, java.time.Duration duration) {
        timers.get(name).record(duration);
    }

    public Timer getTimer(String name) {
        return timers.get(name);
    }
}