package com.example.urbanagent.agent.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 层的全局常量配置。
 */
@ConfigurationProperties(prefix = "urban-agent.agent")
public record AgentConstants(
        /** Agent 唯一标识，与 ToolRegistry 中的名称一致 */
        String agentName,
        /** 聊天工具名 */
        String chatToolName,
        /** 流式聊天工具名 */
        String streamChatToolName,
        /** 恢复会话工具名 */
        String resumeToolName
) {
    public static final String DEFAULT_AGENT_NAME = "urban-management-agent";
    public static final String DEFAULT_CHAT_TOOL = "urban-management-agent.chat";
    public static final String DEFAULT_STREAM_TOOL = "urban-management-agent.streamChat";
    public static final String DEFAULT_RESUME_TOOL = "urban-management-agent.resume";

    public AgentConstants {
        if (agentName == null || agentName.isBlank()) {
            agentName = DEFAULT_AGENT_NAME;
        }
        if (chatToolName == null || chatToolName.isBlank()) {
            chatToolName = DEFAULT_CHAT_TOOL;
        }
        if (streamChatToolName == null || streamChatToolName.isBlank()) {
            streamChatToolName = DEFAULT_STREAM_TOOL;
        }
        if (resumeToolName == null || resumeToolName.isBlank()) {
            resumeToolName = DEFAULT_RESUME_TOOL;
        }
    }
}