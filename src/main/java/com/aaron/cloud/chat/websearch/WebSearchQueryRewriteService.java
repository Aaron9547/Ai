package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.chat.support.ChatAttachmentRetrievalSupport;
import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.dto.model.ModelTokenUsage;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.llm.LlmModelStatus;
import com.aaron.cloud.common.api.enums.llm.LlmWebSearchProvider;
import com.aaron.cloud.common.api.enums.metering.LlmUsageScene;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.model.metering.LlmModelUsageRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
 * 联网问句改写：固定源渠道单次改写问句（{@link #rewriteKeywordsForFixedSources} 返回 1 条检索文本）。
 * 租户可配置 LANGUAGE（提示词 + 流式 LLM）或 WEB_SEARCH（单次 Ark Bot 非流式）；火山 Ark 主检索不经此方法。
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
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final WebSearchProviderRegistry webSearchProviderRegistry;
    private final AesSecretCipher aesSecretCipher;
    private final ObjectMapper objectMapper;
    private final LlmModelUsageRecorder llmModelUsageRecorder;

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
        ResolvedRewriteModel resolved = resolveRewriteModel(tenantId);
        if (resolved != null) {
            try {
                String llmOut =
                        resolved.kind() == LlmModelKind.WEB_SEARCH
                                ? invokeRewriteViaArk(snap, resolved.model(), original, List.of(), 0L, null)
                                : invokeRewriteLlm(tenantId, resolved.model(), original);
                String cleaned = sanitizeLlmOutput(llmOut);
                if (!cleaned.isBlank() && !cleaned.equals(original)) {
                    log.info(
                            "[联网搜索] 问句重写成功（{}）：租户 {}，模型 {}，原文 [{}]，检索词 [{}]",
                            resolved.kind(),
                            tenantId,
                            resolved.model().getAlias(),
                            clipForLog(original),
                            clipForLog(cleaned));
                    return cleaned;
                }
                if (!cleaned.isBlank()) {
                    log.info(
                            "[联网搜索] 问句重写与原文相近，沿用：租户 {}，原文 [{}]",
                            tenantId,
                            clipForLog(original));
                    return clampQuery(cleaned);
                }
                log.warn(
                        "[联网搜索] 问句重写输出无效，回退规则：租户 {}，原文 [{}]",
                        tenantId,
                        clipForLog(original));
            } catch (Exception e) {
                log.warn(
                        "[联网搜索] 问句重写失败，回退规则：租户 {}，原文 [{}]，原因 {}",
                        tenantId,
                        clipForLog(original),
                        e.toString(),
                        e);
            }
        } else {
            log.info(
                    "[联网搜索] 问句重写无可用模型，回退规则：租户 {}，原文 [{}]",
                    tenantId,
                    clipForLog(original));
        }
        String heuristic = heuristicRewriteForRetrievalInput(original);
        log.info(
                "[联网搜索] 问句重写（规则）：租户 {}，原文 [{}]，检索词 [{}]",
                tenantId,
                clipForLog(original),
                clipForLog(heuristic));
        return heuristic;
    }

    /**
     * 固定源渠道：将用户问句改写为单条检索文本。
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
     * 固定源渠道：结合近史将用户问句改写为单条检索文本，供各固定源并行抓取。
     */
    public List<String> rewriteKeywordsForFixedSources(
            TenantSnapshot snap,
            String userQueryPlaintext,
            List<ModelChatRequest.MessageTurn> recentHistory,
            long conversationId,
            LlmUsageScene usageScene) {
        return rewriteKeywordsForFixedSources(
                snap, userQueryPlaintext, recentHistory, conversationId, usageScene, false);
    }

    public List<String> rewriteKeywordsForFixedSources(
            TenantSnapshot snap,
            String userQueryPlaintext,
            List<ModelChatRequest.MessageTurn> recentHistory,
            long conversationId,
            LlmUsageScene usageScene,
            boolean conservativeRewrite) {
        String original = userQueryPlaintext == null ? "" : userQueryPlaintext.trim();
        if (original.isEmpty()) {
            return List.of();
        }
        List<ModelChatRequest.MessageTurn> history =
                recentHistory == null ? List.of() : List.copyOf(recentHistory);
        boolean hasHistory = !history.isEmpty();
        long tenantId = snap.getTenantId();
        if (conservativeRewrite) {
            String heuristic = heuristicRewriteForRetrievalInput(heuristicKeywordSource(original, history));
            log.info(
                    "[联网搜索] 固定源检索问句（附件轮保守规则）：租户 {}，原文 [{}]，近史 {} 条，问句 [{}]",
                    tenantId,
                    clipForLog(original),
                    history.size(),
                    clipForLog(heuristic));
            return heuristic.isBlank() ? List.of() : List.of(heuristic);
        }
        ResolvedRewriteModel resolved = resolveRewriteModel(tenantId);
        boolean tryModel =
                resolved != null && (original.length() >= MIN_LEN_FOR_LLM_REWRITE || hasHistory);
        if (tryModel) {
            try {
                String llmOut =
                        resolved.kind() == LlmModelKind.WEB_SEARCH
                                ? invokeRewriteViaArk(
                                        snap,
                                        resolved.model(),
                                        original,
                                        history,
                                        conversationId,
                                        usageScene)
                                : invokeRewriteLlm(
                                        tenantId,
                                        resolved.model(),
                                        original,
                                        history,
                                        conversationId,
                                        usageScene);
                String cleaned = sanitizeLlmOutput(llmOut);
                if (!cleaned.isBlank()) {
                    log.info(
                            "[联网搜索] 固定源检索问句（{}）：租户 {}，原文 [{}]，近史 {} 条，问句 [{}]",
                            resolved.kind(),
                            tenantId,
                            clipForLog(original),
                            history.size(),
                            clipForLog(cleaned));
                    return List.of(cleaned);
                }
                log.warn(
                        "[联网搜索] 问句重写输出无效，回退规则：租户 {}，原文 [{}]",
                        tenantId,
                        clipForLog(original));
            } catch (Exception e) {
                log.warn(
                        "[联网搜索] 问句重写失败，回退规则：租户 {}，原文 [{}]，原因 {}",
                        tenantId,
                        clipForLog(original),
                        e.toString(),
                        e);
            }
        }
        String heuristic =
                heuristicRewriteForRetrievalInput(heuristicKeywordSource(original, history));
        log.info(
                "[联网搜索] 固定源检索问句（规则）：租户 {}，原文 [{}]，近史 {} 条，问句 [{}]",
                tenantId,
                clipForLog(original),
                history.size(),
                clipForLog(heuristic));
        return heuristic.isBlank() ? List.of() : List.of(heuristic);
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

    private String invokeRewriteLlm(
            long tenantId,
            SysLlmModel lang,
            String original,
            List<ModelChatRequest.MessageTurn> recentHistory,
            long conversationId,
            LlmUsageScene usageScene)
            throws Exception {
        List<ModelChatRequest.MessageTurn> messages =
                buildRewriteMessages(tenantId, original, recentHistory);
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(lang.getAlias());
        req.setThinkingEnabled(false);
        req.setConversationId(conversationId > 0L ? conversationId : null);
        if (usageScene != null) {
            req.setUsageScene(usageScene.getCode());
        }
        req.setMessages(messages);
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return acc.toString();
    }

    private String invokeRewriteViaArk(
            TenantSnapshot snap,
            SysLlmModel webSearchModel,
            String original,
            List<ModelChatRequest.MessageTurn> recentHistory,
            long conversationId,
            LlmUsageScene usageScene)
            throws Exception {
        List<ModelChatRequest.MessageTurn> messages =
                buildRewriteMessages(snap.getTenantId(), original, recentHistory);
        return invokeViaArk(
                snap,
                webSearchModel,
                messages,
                conversationId,
                usageScene,
                "rewrite:" + clipForLog(original));
    }

    private String invokeViaArk(
            TenantSnapshot snap,
            SysLlmModel webSearchModel,
            List<ModelChatRequest.MessageTurn> messages,
            long conversationId,
            LlmUsageScene usageScene,
            String logSummary)
            throws Exception {
        LlmWebSearchProvider providerKind = webSearchModel.resolveWebSearchProvider();
        if (providerKind == null) {
            throw new IllegalStateException("联网重写模型未配置 integration_backend");
        }
        WebSearchModelProvider provider = webSearchProviderRegistry.require(providerKind);
        String apiKey = aesSecretCipher.decryptFromBase64(webSearchModel.getApiKeyCipher());
        long t0 = System.currentTimeMillis();
        WebSearchExecutionResult result =
                provider.execute(
                        webSearchModel,
                        apiKey,
                        new WebSearchArkInvokeRequest(logSummary, messages));
        recordArkRewriteUsage(
                snap,
                webSearchModel,
                result.rawResponseBody(),
                conversationId,
                System.currentTimeMillis() - t0,
                usageScene);
        WebGroundingBundle bundle = result.bundle();
        if (bundle == null || bundle.summaryText() == null) {
            return "";
        }
        return bundle.summaryText();
    }

    private void recordArkRewriteUsage(
            TenantSnapshot snap,
            SysLlmModel webSearchModel,
            String rawJson,
            long conversationId,
            long durationMs,
            LlmUsageScene usageScene) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            ModelTokenUsage usage = ChatWebSearchGroundingService.parseWebSearchUsage(root);
            if (usage == null || usage.totalTokens() <= 0) {
                return;
            }
            llmModelUsageRecorder.recordAfterLlmUsage(
                    snap,
                    webSearchModel,
                    webSearchModel.getAlias(),
                    conversationId,
                    usage,
                    durationMs,
                    usageScene);
        } catch (Exception ex) {
            log.debug("[联网搜索] 跳过问句重写 Ark 用量记录：{}", ex.toString());
        }
    }

    private List<ModelChatRequest.MessageTurn> buildRewriteMessages(
            long tenantId, String original, List<ModelChatRequest.MessageTurn> recentHistory) {
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
        var messages = new ArrayList<ModelChatRequest.MessageTurn>();
        messages.add(sysTurn);
        WebSearchArkContextBuilder.appendRecentUserAssistantTurns(messages, recentHistory);
        messages.add(userTurn);
        return List.copyOf(messages);
    }

    private ResolvedRewriteModel resolveRewriteModel(long tenantId) {
        var configured = tenantRuntimeSettingApplicationService.webSearchQueryRewriteModelId(tenantId);
        if (configured.isPresent()) {
            SysLlmModel row =
                    llmModelRepository.findById(tenantId, configured.get()).orElse(null);
            ResolvedRewriteModel usable = asUsableRewriteModel(row);
            if (usable != null) {
                return usable;
            }
            log.warn(
                    "[联网搜索] 问句重写配置模型不可用 tenantId={} modelId={}，回退默认",
                    tenantId,
                    configured.get());
        }
        SysLlmModel lang = llmModelRepository.pickDefaultLanguageModel(tenantId).orElse(null);
        SysLlmModel usableLang = usableLanguageModel(lang);
        return usableLang != null ? new ResolvedRewriteModel(usableLang, LlmModelKind.LANGUAGE) : null;
    }

    private static ResolvedRewriteModel asUsableRewriteModel(SysLlmModel row) {
        if (row == null) {
            return null;
        }
        LlmModelKind kind = row.getModelKind() != null ? row.getModelKind() : LlmModelKind.LANGUAGE;
        if (kind == LlmModelKind.LANGUAGE) {
            SysLlmModel lang = usableLanguageModel(row);
            return lang != null ? new ResolvedRewriteModel(lang, LlmModelKind.LANGUAGE) : null;
        }
        if (kind == LlmModelKind.WEB_SEARCH) {
            SysLlmModel web = usableWebSearchModel(row);
            return web != null ? new ResolvedRewriteModel(web, LlmModelKind.WEB_SEARCH) : null;
        }
        return null;
    }

    private static SysLlmModel usableLanguageModel(SysLlmModel lang) {
        if (lang == null) {
            return null;
        }
        LlmModelKind k = lang.getModelKind() != null ? lang.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.LANGUAGE || lang.getStatus() != LlmModelStatus.ACTIVE) {
            return null;
        }
        try {
            LlmModelKindPolicy.assertLanguageModelForChatStream(lang);
            return lang;
        } catch (Exception ex) {
            return null;
        }
    }

    private static SysLlmModel usableWebSearchModel(SysLlmModel row) {
        if (row == null || row.getStatus() != LlmModelStatus.ACTIVE) {
            return null;
        }
        if (row.getModelKind() != LlmModelKind.WEB_SEARCH) {
            return null;
        }
        if (row.resolveWebSearchProvider() != LlmWebSearchProvider.VOLCENGINE_ARK_BOT) {
            return null;
        }
        String botId = row.getOpenaiModelId() == null ? "" : row.getOpenaiModelId().trim();
        if (botId.isBlank()) {
            return null;
        }
        String url = ArkBotChatCompletionsUrl.normalize(row.getOpenaiBaseUrl());
        if (url.isBlank()) {
            return null;
        }
        if (row.getApiKeyCipher() == null || row.getApiKeyCipher().isBlank()) {
            return null;
        }
        return row;
    }

    private String invokeRewriteLlm(long tenantId, SysLlmModel lang, String original) throws Exception {
        return invokeRewriteLlm(tenantId, lang, original, List.of(), 0L, null);
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

    /** 含附件摘要结构时优先保留附件实体 + 用户意图，避免泛化为无关热点词。 */
    static String heuristicRewriteForRetrievalInput(String raw) {
        return clampQuery(
                ChatAttachmentRetrievalSupport.heuristicRewriteForRetrievalInput(
                        raw, WebSearchQueryRewriteService::heuristicRewrite));
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

    private record ResolvedRewriteModel(SysLlmModel model, LlmModelKind kind) {}
}
