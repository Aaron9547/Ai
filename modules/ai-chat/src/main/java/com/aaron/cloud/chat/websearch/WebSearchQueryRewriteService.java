package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 第三方固定源/新闻检索前，将对话问句改写为更短、更聚焦的检索词，提高命中率。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchQueryRewriteService {

    private static final int MAX_SEARCH_QUERY_CHARS = 120;
    private static final int MIN_LEN_FOR_LLM_REWRITE = 12;

    private static final Pattern FILLER_PREFIX =
            Pattern.compile(
                    "^(请|帮我|帮忙|能否|可以|麻烦|我想|我要|想知道|请问|告诉我|说说|介绍一下|查一下|搜索一下|联网查|联网搜|联网搜索|联网|上网搜索|上网|搜索|查找)+");

    private final SysLlmModelRepository llmModelRepository;
    private final ModelInvokePort modelInvokePort;
    private final PromptTemplateResolvePort promptTemplates;

    /**
     * @return 用于外呼 DuckDuckGo/维基/Google/百度 等的检索文本（可能与原文不同）
     */
    public String rewriteForSearch(TenantSnapshot snap, String userQueryPlaintext) {
        String original = userQueryPlaintext == null ? "" : userQueryPlaintext.trim();
        if (original.isEmpty()) {
            log.info("[联网搜索] 问句重写跳过：原文为空");
            return "";
        }
        if (original.length() < MIN_LEN_FOR_LLM_REWRITE) {
            String kept = clampQuery(original);
            log.info(
                    "[联网搜索] 问句重写跳过（过短）：原文 [{}]，检索词 [{}]",
                    clipForLog(original),
                    clipForLog(kept));
            return kept;
        }
        long tenantId = snap.getTenantId();
        SysLlmModel lang = resolveLanguageModel(tenantId);
        if (lang != null) {
            try {
                String llmOut = invokeRewriteLlm(tenantId, lang, original);
                String cleaned = sanitizeLlmOutput(llmOut);
                if (!cleaned.isBlank() && !cleaned.equals(original)) {
                    log.info(
                            "[联网搜索] 问句重写成功（LLM）：租户 {}，模型 {}，原文 [{}]，检索词 [{}]",
                            tenantId,
                            lang.getAlias(),
                            clipForLog(original),
                            clipForLog(cleaned));
                    return cleaned;
                }
                if (!cleaned.isBlank()) {
                    log.info(
                            "[联网搜索] 问句重写 LLM 与原文相近，沿用：租户 {}，原文 [{}]",
                            tenantId,
                            clipForLog(original));
                    return clampQuery(cleaned);
                }
                log.warn(
                        "[联网搜索] 问句重写 LLM 输出无效，回退规则：租户 {}，原文 [{}]",
                        tenantId,
                        clipForLog(original));
            } catch (Exception e) {
                log.warn(
                        "[联网搜索] 问句重写 LLM 失败，回退规则：租户 {}，原文 [{}]，原因 {}",
                        tenantId,
                        clipForLog(original),
                        e.toString(),
                        e);
            }
        } else {
            log.info(
                    "[联网搜索] 问句重写无可用 LANGUAGE 模型，回退规则：租户 {}，原文 [{}]",
                    tenantId,
                    clipForLog(original));
        }
        String heuristic = heuristicRewrite(original);
        log.info(
                "[联网搜索] 问句重写（规则）：租户 {}，原文 [{}]，检索词 [{}]",
                tenantId,
                clipForLog(original),
                clipForLog(heuristic));
        return heuristic;
    }

    private SysLlmModel resolveLanguageModel(long tenantId) {
        SysLlmModel lang = llmModelRepository.pickDefaultLanguageModel(tenantId).orElse(null);
        if (lang == null) {
            return null;
        }
        LlmModelKind k = lang.getModelKind() != null ? lang.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.LANGUAGE) {
            return null;
        }
        try {
            LlmModelKindPolicy.assertLanguageModelForChatStream(lang);
            return lang;
        } catch (Exception ex) {
            return null;
        }
    }

    private String invokeRewriteLlm(long tenantId, SysLlmModel lang, String original) throws Exception {
        LocalDate today = LocalDate.now();
        Map<String, String> vars =
                Map.of(
                        "user_message",
                        original,
                        "today",
                        today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        "year",
                        String.valueOf(today.getYear()));
        String system =
                promptTemplates.renderSystem(
                        "web_search_query_rewrite_system", tenantId, "zh-CN", vars);
        String user =
                promptTemplates.renderUser(
                        "web_search_query_rewrite_user", tenantId, "zh-CN", vars);
        var sysTurn = new ModelChatRequest.MessageTurn();
        sysTurn.setRole("system");
        sysTurn.setContent(system);
        var userTurn = new ModelChatRequest.MessageTurn();
        userTurn.setRole("user");
        userTurn.setContent(user);
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(lang.getAlias());
        req.setThinkingEnabled(false);
        req.setMessages(List.of(sysTurn, userTurn));
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return acc.toString();
    }

    static String sanitizeLlmOutput(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String line =
                raw.lines()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .findFirst()
                        .orElse("");
        line = line.replaceAll("^[\"'「『【]+|[\"'」』】]+$", "").trim();
        return clampQuery(line);
    }

    static String heuristicRewrite(String raw) {
        String t = raw.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        String prev;
        do {
            prev = t;
            t = FILLER_PREFIX.matcher(t).replaceFirst("").trim();
        } while (!t.equals(prev) && !t.isEmpty());
        int q = t.indexOf('？');
        if (q < 0) {
            q = t.indexOf('?');
        }
        if (q > 0 && q < 80) {
            t = t.substring(0, q).trim();
        }
        t = t.replaceAll("[,，;；|｜]+", " ").replaceAll("\\s+", " ").trim();
        return clampQuery(t);
    }

    static String clampQuery(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= MAX_SEARCH_QUERY_CHARS) {
            return t;
        }
        return t.substring(0, MAX_SEARCH_QUERY_CHARS).trim();
    }

    static String clipForLog(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ');
        return t.length() <= 200 ? t : t.substring(0, 200) + "…";
    }
}
