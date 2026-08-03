package com.aaron.cloud.chat.prompt;

import com.aaron.cloud.chat.ChatResponseLocalePrompt;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 对话模块内提示词解析封装（DB → 平台默认 → BuiltinCatalog）。 */
@Component
@RequiredArgsConstructor
public class ChatPromptTemplateSupport {

    private final PromptTemplateResolvePort promptTemplates;

    public String assistantPersona(long tenantId, String responseLocale) {
        String loc = ChatResponseLocalePrompt.normalize(responseLocale);
        String text = promptTemplates.resolveFragment("chat_assistant_persona", tenantId, loc);
        if (text == null || text.isBlank()) {
            return ChatResponseLocalePrompt.baseAssistantPersona(responseLocale);
        }
        return text;
    }

    public void appendLanguageDirective(StringBuilder sys, long tenantId, String responseLocale) {
        String loc = ChatResponseLocalePrompt.normalize(responseLocale);
        String text = promptTemplates.resolveFragment("chat_language_directive", tenantId, loc);
        if (text == null || text.isBlank()) {
            ChatResponseLocalePrompt.appendLanguageDirective(sys, responseLocale);
            return;
        }
        sys.append(text);
    }

    public String crossSessionBlockHeader(long tenantId, String responseLocale) {
        String loc = ChatResponseLocalePrompt.normalize(responseLocale);
        return promptTemplates.resolveFragment("profile_guard_header", tenantId, loc);
    }

    public void appendProfileGuardUsage(
            StringBuilder sys, long tenantId, String responseLocale, boolean currentWindowHasNoPriorTurns) {
        String loc = ChatResponseLocalePrompt.normalize(responseLocale);
        String extra = "";
        if (currentWindowHasNoPriorTurns) {
            extra =
                    promptTemplates.resolveFragment("profile_guard_first_turn_extra", tenantId, loc);
            if (extra == null) {
                extra = "";
            }
        }
        Map<String, String> vars = Map.of("first_turn_extra", extra);
        String usage =
                PromptTemplateApplicationServiceBridge.render(
                        promptTemplates, "profile_guard_usage", tenantId, loc, vars);
        if (usage == null || usage.isBlank()) {
            ProfilePromptGuardFallback.appendUsageDirective(sys, responseLocale, currentWindowHasNoPriorTurns);
            return;
        }
        sys.append(usage);
    }

    public String ragSnippetHeader(long tenantId) {
        return promptTemplates.resolveFragment("rag_snippet_header", tenantId, "zh-CN");
    }

    public String ragSnippetHeaderWebHint(long tenantId) {
        return promptTemplates.resolveFragment("rag_snippet_header_web_hint", tenantId, "zh-CN");
    }

    public String websearchSummaryHeader(long tenantId) {
        return promptTemplates.resolveFragment("websearch_summary_header", tenantId, "zh-CN");
    }

    public String websearchCitationHeader(long tenantId) {
        return promptTemplates.resolveFragment("websearch_citation_header", tenantId, "zh-CN");
    }

    public String userAttachmentMarker(long tenantId) {
        return promptTemplates.resolveFragment("user_attachment_marker", tenantId, "zh-CN");
    }

    public List<String> followUpFallbacks(long tenantId) {
        List<String> series = promptTemplates.resolveFragmentSeries("follow_up_fallback", tenantId, "zh-CN");
        return series.isEmpty()
                ? List.of("能再具体说说吗？", "还有其他需要注意的吗？", "请举一个实际例子")
                : series;
    }

    public List<String> starterEmptyFallbacks(long tenantId) {
        List<String> series = promptTemplates.resolveFragmentSeries("starter_empty_fallback", tenantId, "zh-CN");
        return series.isEmpty()
                ? List.of("写一首关于春天的诗", "用通俗语言解释量子纠缠", "帮我生成一份周报模板")
                : series;
    }

    /** 避免 ai-chat 直接依赖 ai-prompt 模块类。 */
    static final class PromptTemplateApplicationServiceBridge {
        private PromptTemplateApplicationServiceBridge() {}

        static String render(
                PromptTemplateResolvePort port,
                String code,
                long tenantId,
                String locale,
                Map<String, String> variables) {
            return port.renderUser(code, tenantId, locale, variables);
        }
    }

    /** 无 DB/兜底时的静态回退（与历史 ProfilePromptGuard 一致）。 */
    static final class ProfilePromptGuardFallback {
        private ProfilePromptGuardFallback() {}

        static void appendUsageDirective(
                StringBuilder sys, String responseLocale, boolean currentWindowHasNoPriorTurns) {
            com.aaron.cloud.chat.ProfilePromptGuard.appendUsageDirective(
                    sys, responseLocale, currentWindowHasNoPriorTurns);
        }
    }
}
