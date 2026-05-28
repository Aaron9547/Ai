package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.metering.LlmUsageScene;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 联网问句改写：固定源渠道拆三关键词（{@link #rewriteKeywordsForFixedSources}）。
 * {@link #rewriteForSearch} 保留供测试或后续场景；火山 Ark 直连不经此方法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchQueryRewriteService {

    private static final int MAX_SEARCH_QUERY_CHARS = 120;
    private static final int MAX_KEYWORD_CHARS = 32;
    private static final int FIXED_SOURCE_KEYWORD_COUNT = 3;
    private static final int MIN_LEN_FOR_LLM_REWRITE = 12;

    private static final ObjectMapper KEYWORD_JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

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

    /**
     * 固定源渠道：从用户问句抽取恰好 {@value #FIXED_SOURCE_KEYWORD_COUNT} 个检索关键词，供多源并行抓取。
     */
    public List<String> rewriteKeywordsForFixedSources(TenantSnapshot snap, String userQueryPlaintext) {
        return rewriteKeywordsForFixedSources(snap, userQueryPlaintext, List.of(), 0L);
    }

    public List<String> rewriteKeywordsForFixedSources(
            TenantSnapshot snap,
            String userQueryPlaintext,
            List<ModelChatRequest.MessageTurn> recentHistory,
            long conversationId) {
        return rewriteKeywordsForFixedSources(
                snap, userQueryPlaintext, recentHistory, conversationId, null);
    }

    /**
     * 固定源渠道：结合近史与当前问句拆三关键词；{@code conversationId} 与 {@code usageScene} 写入 LLM 计量流水。
     */
    public List<String> rewriteKeywordsForFixedSources(
            TenantSnapshot snap,
            String userQueryPlaintext,
            List<ModelChatRequest.MessageTurn> recentHistory,
            long conversationId,
            LlmUsageScene usageScene) {
        String original = userQueryPlaintext == null ? "" : userQueryPlaintext.trim();
        if (original.isEmpty()) {
            return List.of();
        }
        List<ModelChatRequest.MessageTurn> history =
                recentHistory == null ? List.of() : List.copyOf(recentHistory);
        boolean hasHistory = !history.isEmpty();
        long tenantId = snap.getTenantId();
        SysLlmModel lang = resolveLanguageModel(tenantId);
        boolean tryLlm =
                lang != null && (original.length() >= MIN_LEN_FOR_LLM_REWRITE || hasHistory);
        if (tryLlm) {
            try {
                String llmOut =
                        invokeFixedKeywordsLlm(
                                tenantId, lang, original, history, conversationId, usageScene);
                List<String> parsed = parseKeywordJson(llmOut);
                if (!parsed.isEmpty()) {
                    List<String> normalized =
                            normalizeKeywordTriplet(parsed, heuristicKeywordSource(original, history));
                    log.info(
                            "[联网搜索] 固定源三关键词（LLM）：租户 {}，原文 [{}]，近史 {} 条，关键词 {}",
                            tenantId,
                            clipForLog(original),
                            history.size(),
                            normalized);
                    return normalized;
                }
                log.warn(
                        "[联网搜索] 固定源关键词 LLM 解析为空，回退规则：租户 {}，原文 [{}]",
                        tenantId,
                        clipForLog(original));
            } catch (Exception e) {
                log.warn(
                        "[联网搜索] 固定源关键词 LLM 失败，回退规则：租户 {}，原文 [{}]，原因 {}",
                        tenantId,
                        clipForLog(original),
                        e.toString(),
                        e);
            }
        }
        List<String> heuristic = heuristicKeywords(heuristicKeywordSource(original, history));
        log.info(
                "[联网搜索] 固定源三关键词（规则）：租户 {}，原文 [{}]，近史 {} 条，关键词 {}",
                tenantId,
                clipForLog(original),
                history.size(),
                heuristic);
        return heuristic;
    }

    static String heuristicKeywordSource(
            String currentTurn, List<ModelChatRequest.MessageTurn> recentHistory) {
        if (recentHistory == null || recentHistory.isEmpty()) {
            return currentTurn;
        }
        StringBuilder sb = new StringBuilder();
        for (ModelChatRequest.MessageTurn t : recentHistory) {
            if (t == null || t.getRole() == null) {
                continue;
            }
            String role = t.getRole().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String body = t.getContent() == null ? "" : t.getContent().trim();
            if (body.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(body);
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(currentTurn == null ? "" : currentTurn.trim());
        return sb.toString().trim();
    }

    static List<String> parseKeywordJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }
        try {
            List<String> list = KEYWORD_JSON.readValue(trimmed, STRING_LIST);
            if (list == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (String item : list) {
                if (item == null) {
                    continue;
                }
                String t = clampKeyword(item.trim());
                if (!t.isBlank()) {
                    out.add(t);
                }
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static List<String> normalizeKeywordTriplet(List<String> candidates, String fallbackSource) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String c : candidates) {
            String t = clampKeyword(c == null ? "" : c.trim());
            if (!t.isBlank()) {
                unique.add(t);
            }
            if (unique.size() >= FIXED_SOURCE_KEYWORD_COUNT) {
                break;
            }
        }
        List<String> tokens = tokenizeForKeywords(fallbackSource);
        int ti = 0;
        while (unique.size() < FIXED_SOURCE_KEYWORD_COUNT && ti < tokens.size()) {
            unique.add(tokens.get(ti++));
        }
        if (unique.isEmpty()) {
            unique.add(clampKeyword(fallbackSource));
        }
        while (unique.size() < FIXED_SOURCE_KEYWORD_COUNT) {
            String pad = unique.iterator().next();
            unique.add(pad);
        }
        return List.copyOf(unique).subList(0, FIXED_SOURCE_KEYWORD_COUNT);
    }

    static List<String> heuristicKeywords(String raw) {
        return normalizeKeywordTriplet(tokenizeForKeywords(heuristicRewrite(raw)), raw);
    }

    private static List<String> tokenizeForKeywords(String text) {
        String t = text == null ? "" : text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) {
            return List.of();
        }
        String[] parts = t.split("[\\s,，;；|｜/、]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String k = clampKeyword(p.trim());
            if (k.length() >= 2 && !out.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    static String clampKeyword(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= MAX_KEYWORD_CHARS) {
            return t;
        }
        return t.substring(0, MAX_KEYWORD_CHARS).trim();
    }

    private String invokeFixedKeywordsLlm(
            long tenantId,
            SysLlmModel lang,
            String original,
            List<ModelChatRequest.MessageTurn> recentHistory,
            long conversationId,
            LlmUsageScene usageScene)
            throws Exception {
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
                        "web_search_fixed_keywords_system", tenantId, "zh-CN", vars);
        String user =
                promptTemplates.renderUser("web_search_fixed_keywords_user", tenantId, "zh-CN", vars);
        var sysTurn = new ModelChatRequest.MessageTurn();
        sysTurn.setRole("system");
        sysTurn.setContent(system);
        var userTurn = new ModelChatRequest.MessageTurn();
        userTurn.setRole("user");
        userTurn.setContent(user);
        var messages = new ArrayList<ModelChatRequest.MessageTurn>();
        messages.add(sysTurn);
        WebSearchArkContextBuilder.appendRecentUserAssistantTurns(messages, recentHistory);
        messages.add(userTurn);
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(lang.getAlias());
        req.setThinkingEnabled(false);
        req.setConversationId(conversationId > 0L ? conversationId : null);
        if (usageScene != null) {
            req.setUsageScene(usageScene.getCode());
        }
        req.setMessages(List.copyOf(messages));
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return acc.toString();
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
