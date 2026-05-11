package com.aaron.cloud.chat;

import com.aaron.cloud.common.api.enums.ChatInputBlockReason;
import com.aaron.cloud.common.config.properties.AiChatInputGuardProperties;
import com.aaron.cloud.common.guardrail.GuardrailSensitiveTermRepository;
import com.aaron.cloud.common.guardrail.GuardrailRuleRepository;
import com.aaron.cloud.common.guardrail.entity.GuardrailRule;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 瀵硅瘽鍙戦€佸墠鍚屾杈撳叆鎶ゆ爮锛氭湁鏁堟€с€佹晱鎰熻瘝瀛愪覆銆佹彁绀鸿瘝鏀诲嚮鐗瑰緛姝ｅ垯銆佺鎴?{@code guardrail_rule}銆? *
 * <p>鍛戒腑鏃朵笉鎶?REST 寮傚父锛岀敱 {@link ChatApplicationService} 璧?SSE 鍥哄畾鍔濆璇垎鏀紝閬垮厤 {@code text/event-stream} 璇蛋
 * {@code GlobalExceptionHandler} JSON銆? */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInputGuardService {

    private final AiChatInputGuardProperties properties;
    private final GuardrailSensitiveTermRepository sensitiveTermRepository;
    private final GuardrailRuleRepository guardrailRuleRepository;

    private volatile List<Pattern> compiledInjectionPatterns = List.of();

    @PostConstruct
    void compileInjectionPatterns() {
        List<Pattern> next = new ArrayList<>();
        List<String> raw = properties.getPromptInjectionRegexPatterns();
        if (raw != null) {
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
        }
        compiledInjectionPatterns = List.copyOf(next);
    }

    /** @param guardrailRuleName 浠呭綋 {@code reason == GUARDRAIL_POLICY} 鏃舵湁鍊硷紝鐢ㄤ簬杩愮淮鏃ュ織锛屽嬁鍐欏叆鐢ㄦ埛鍙瀛楁 */
    public record InputGuardOutcome(ChatInputBlockReason reason, String guardrailRuleName) {}

    public Optional<InputGuardOutcome> evaluate(long tenantId, String userText) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        String text = userText == null ? "" : userText;
        String trimmed = text.trim();
        if (trimmed.length() < properties.getMinUserTextChars()) {
            return Optional.of(new InputGuardOutcome(ChatInputBlockReason.INVALID_INPUT, null));
        }
        if (text.length() > properties.getMaxUserTextChars()) {
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
        List<String> extraYaml = properties.getSensitiveWords();
        if (extraYaml != null) {
            for (String w : extraYaml) {
                if (w == null || w.isBlank()) {
                    continue;
                }
                if (containsInsensitiveSubstring(text, w.trim())) {
                    return Optional.of(new InputGuardOutcome(ChatInputBlockReason.SENSITIVE_CONTENT, null));
                }
            }
        }

        for (Pattern pat : compiledInjectionPatterns) {
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
                        "skip invalid guardrail regex tenantId={} ruleId={} ruleName={} pattern={}",
                        tenantId,
                        rule.getId(),
                        rule.getName(),
                        rule.getPattern(),
                        e);
            }
        }
        return Optional.empty();
    }

    public String blockedReplyTemplate() {
        return properties.getBlockedReplyTemplate();
    }

    /**
     * 鏄庢樉鏃犳湁鏁堣涔夛細鍗曞瓧绗﹂噸澶嶅崰姣旇繃楂樻垨浠呮爣鐐?绌虹櫧缁勫悎锛堥槻鏃犳剰涔夊埛鎺ュ彛锛夈€?     */
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
