package com.aaron.cloud.chat;

import com.aaron.cloud.common.api.enums.chat.ChatInputBlockReason;
import com.aaron.cloud.common.guardrail.GuardrailRuleRepository;
import com.aaron.cloud.common.guardrail.GuardrailSensitiveTermRepository;
import com.aaron.cloud.common.guardrail.entity.GuardrailRule;
import com.aaron.cloud.common.tenant.runtime.ChatInputGuardRuntime;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对话发送前同步输入护栏：有效性、敏感词子串、提示词攻击特征、租户 {@code guardrail_rule}。
 *
 * <p>命中时不抛 REST 异常，由 {@link ChatApplicationService} 走 SSE 固定劝导语分支，避免 {@code text/event-stream} 误走
 * {@code GlobalExceptionHandler} JSON。开关与阈值见租户运行参数 {@code CHAT_INPUT_GUARD_JSON}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInputGuardService {

    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final GuardrailSensitiveTermRepository sensitiveTermRepository;
    private final GuardrailRuleRepository guardrailRuleRepository;

    /** @param guardrailRuleName 仅当 {@code reason == GUARDRAIL_POLICY} 时有值，用于运维日志，勿写入用户可见字段 */
    public record InputGuardOutcome(ChatInputBlockReason reason, String guardrailRuleName) {}

    public Optional<InputGuardOutcome> evaluate(long tenantId, String userText) {
        ChatInputGuardRuntime g = tenantRuntimeSettingApplicationService.chatInputGuardEffective(tenantId);
        if (!g.enabled()) {
            return Optional.empty();
        }
        String text = userText == null ? "" : userText;
        String trimmed = text.trim();
        if (trimmed.length() < g.minUserTextChars()) {
            return Optional.of(new InputGuardOutcome(ChatInputBlockReason.INVALID_INPUT, null));
        }
        if (text.length() > g.maxUserTextChars()) {
            return Optional.of(new InputGuardOutcome(ChatInputBlockReason.INVALID_INPUT, null));
        }
        if (trimmed.isEmpty()) {
            return Optional.of(new InputGuardOutcome(ChatInputBlockReason.INVALID_INPUT, null));
        }
        if (isLowInformationNoise(trimmed)) {
            return Optional.of(new InputGuardOutcome(ChatInputBlockReason.INVALID_INPUT, null));
        }

        for (String w : sensitiveTermRepository.listEffectiveWordsForChatTenant(tenantId)) {
            if (w == null || w.isBlank()) {
                continue;
            }
            if (containsInsensitiveSubstring(text, w.strip())) {
                return Optional.of(new InputGuardOutcome(ChatInputBlockReason.SENSITIVE_CONTENT, null));
            }
        }
        List<String> extra = g.sensitiveWordExtras();
        if (extra != null) {
            for (String w : extra) {
                if (w == null || w.isBlank()) {
                    continue;
                }
                if (containsInsensitiveSubstring(text, w.trim())) {
                    return Optional.of(new InputGuardOutcome(ChatInputBlockReason.SENSITIVE_CONTENT, null));
                }
            }
        }

        for (Pattern pat : g.compiledInjectionPatterns()) {
            if (pat.matcher(text).find()) {
                return Optional.of(new InputGuardOutcome(ChatInputBlockReason.PROMPT_INJECTION_ATTEMPT, null));
            }
        }

        for (GuardrailRule rule : guardrailRuleRepository.listByTenant(tenantId)) {
            try {
                if (Pattern.compile(rule.getPattern()).matcher(text).find()) {
                    return Optional.of(new InputGuardOutcome(ChatInputBlockReason.GUARDRAIL_POLICY, rule.getName()));
                }
            } catch (PatternSyntaxException e) {
                log.warn(
                        "[输入安全] 跳过无效正则规则：租户 {}，规则编号 {}，名称 {}，表达式 {}",
                        tenantId,
                        rule.getId(),
                        rule.getName(),
                        rule.getPattern(),
                        e);
            }
        }
        return Optional.empty();
    }

    public String blockedReplyTemplate(long tenantId) {
        return tenantRuntimeSettingApplicationService.chatInputGuardEffective(tenantId).blockedReplyTemplate();
    }

    private static boolean isLowInformationNoise(String trimmed) {
        if (trimmed.length() < 8) {
            return false;
        }
        int lettersOrDigits = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                lettersOrDigits++;
            }
        }
        if (trimmed.length() >= 32 && lettersOrDigits * 10 < trimmed.length()) {
            return true;
        }
        if (trimmed.length() >= 16) {
            int[] counts = new int[65536];
            int max = 0;
            for (int i = 0; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c < counts.length) {
                    int n = ++counts[c];
                    max = Math.max(max, n);
                }
            }
            if (max * 3 >= trimmed.length() * 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsInsensitiveSubstring(String haystack, String needle) {
        boolean asciiWord =
                needle.chars().allMatch(c -> c < 128 && (Character.isLetterOrDigit(c) || c == '_' || c == '-'));
        if (asciiWord) {
            return haystack.toLowerCase().contains(needle.toLowerCase());
        }
        return haystack.contains(needle);
    }
}
