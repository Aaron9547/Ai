package com.aaron.cloud.common.config.properties;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * C 端对话发送前的同步输入护栏（敏感词、提示词攻击特征、长度与租户 guardrail 正则）。
 *
 * <p>见规则文档 {@code .cursorrules} §5.2；总开关见 {@code ai.chat.input-guard.enabled} / {@code AI_CHAT_INPUT_GUARD_ENABLED}。
 */
@Data
@ConfigurationProperties(prefix = "ai.chat.input-guard")
public class AiChatInputGuardProperties {

    private boolean enabled = true;

    /** 去空白后的最小有效字符数（为 1 时仅排除纯空白）。 */
    private int minUserTextChars = 1;

    private int maxUserTextChars = 8000;

    /**
     * 拦截后助手固定劝导语（不调用大模型）；勿包含用户原文。
     */
    private String blockedReplyTemplate =
            "您好，您发送的内容未通过平台安全校验，我们无法继续处理该请求。\n\n"
                    + "请避免包含不当用语、试图修改系统或绕过安全策略的措辞，并尽量用清晰、正常的问题描述您的需求。"
                    + "若您认为属于误判，可调整表述后重试，或联系管理员。";

    /**
     * 运维补充敏感词（子串匹配）；主词库在表 {@code guardrail_sensitive_term}（平台强制 + 租户扩展），本列表与之合并生效。
     */
    private List<String> sensitiveWords = new ArrayList<>();

    /**
     * 提示词攻击 / 越狱类特征正则（单行一条）；语法错误时启动跳过该条并打 WARN。
     */
    private List<String> promptInjectionRegexPatterns = defaultPromptPatterns();

    private static List<String> defaultPromptPatterns() {
        return List.of(
                "(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions?",
                "(?i)disregard\\s+(the\\s+)?(above|system|developer)\\s*(message|prompt)?",
                "(?i)you\\s+are\\s+now\\s+(DAN|jailbreak|unrestricted)",
                "(?i)system\\s*prompt\\s*(is|:)",
                "(?i)reveal\\s+(your\\s+)?(hidden\\s+)?(system|developer)\\s*prompt",
                "(?i)override\\s+(safety|security|content)\\s*(policy|filter)?",
                "(?i)\\[\\[\\s*INST"); // [[INST]] 风格注入片段
    }
}
