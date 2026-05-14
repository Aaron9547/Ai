package com.aaron.cloud.common.tenant.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;

/**
 * 对话输入护栏运行态（来自 {@code CHAT_INPUT_GUARD_JSON}）；{@code {}} 表示使用代码内置默认。
 */
@Slf4j
public record ChatInputGuardRuntime(
        boolean enabled,
        int minUserTextChars,
        int maxUserTextChars,
        String blockedReplyTemplate,
        List<String> sensitiveWordExtras,
        List<Pattern> compiledInjectionPatterns) {

    private static final String DEFAULT_BLOCKED =
            "您好，您发送的内容未通过平台安全校验，我们无法继续处理该请求。\n\n"
                    + "请避免包含不当用语、试图修改系统或绕过安全策略的措辞，并尽量用清晰、正常的问题描述您的需求。"
                    + "若您认为属于误判，可调整表述后重试，或联系管理员。";

    public static ChatInputGuardRuntime parse(String rawJson, ObjectMapper objectMapper) {
        boolean enabled = true;
        int min = 1;
        int max = 8000;
        String template = DEFAULT_BLOCKED;
        List<String> extras = new ArrayList<>();
        List<String> rawPatterns = defaultPromptPatterns();
        if (rawJson != null && !rawJson.isBlank()) {
            try {
                JsonNode n = objectMapper.readTree(rawJson);
                if (n.isObject()) {
                    JsonNode en = n.get("enabled");
                    if (en != null && !en.isNull()) {
                        if (en.isBoolean()) {
                            enabled = en.asBoolean();
                        } else if (en.isTextual()) {
                            enabled = Boolean.parseBoolean(en.asText().trim());
                        }
                    }
                    if (n.has("minUserTextChars") && n.get("minUserTextChars").isNumber()) {
                        min = Math.max(1, n.get("minUserTextChars").asInt(1));
                    }
                    if (n.has("maxUserTextChars") && n.get("maxUserTextChars").isNumber()) {
                        max = Math.max(min, n.get("maxUserTextChars").asInt(8000));
                    }
                    JsonNode tpl = n.get("blockedReplyTemplate");
                    if (tpl != null && tpl.isTextual()) {
                        String t = tpl.asText().trim();
                        if (!t.isEmpty()) {
                            template = t;
                        }
                    }
                    JsonNode sw = n.get("sensitiveWords");
                    if (sw != null && sw.isArray()) {
                        for (JsonNode el : sw) {
                            if (el != null && el.isTextual()) {
                                String s = el.asText().trim();
                                if (!s.isEmpty()) {
                                    extras.add(s);
                                }
                            }
                        }
                    }
                    JsonNode pr = n.get("promptInjectionRegexPatterns");
                    if (pr != null && pr.isArray() && pr.size() > 0) {
                        rawPatterns = new ArrayList<>();
                        for (JsonNode el : pr) {
                            if (el != null && el.isTextual()) {
                                String s = el.asText();
                                if (s != null && !s.isBlank()) {
                                    rawPatterns.add(s);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("CHAT_INPUT_GUARD_JSON parse fallback to defaults: {}", e.toString());
            }
        }
        return new ChatInputGuardRuntime(
                enabled, min, max, template, List.copyOf(extras), compilePatterns(rawPatterns));
    }

    private static List<String> defaultPromptPatterns() {
        return List.of(
                "(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions?",
                "(?i)disregard\\s+(the\\s+)?(above|system|developer)\\s*(message|prompt)?",
                "(?i)you\\s+are\\s+now\\s+(DAN|jailbreak|unrestricted)",
                "(?i)system\\s*prompt\\s*(is|:)",
                "(?i)reveal\\s+(your\\s+)?(hidden\\s+)?(system|developer)\\s*prompt",
                "(?i)override\\s+(safety|security|content)\\s*(policy|filter)?",
                "(?i)\\[\\[\\s*INST");
    }

    private static List<Pattern> compilePatterns(List<String> raw) {
        List<Pattern> next = new ArrayList<>();
        if (raw == null) {
            return List.of();
        }
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            try {
                next.add(Pattern.compile(s));
            } catch (PatternSyntaxException e) {
                log.warn("skip invalid prompt-injection regex pattern={}", s, e);
            }
        }
        return List.copyOf(next);
    }
}
