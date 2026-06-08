package com.aaron.cloud.chat.rag;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.llm.LlmModelStatus;
import com.aaron.cloud.common.api.enums.metering.LlmUsageScene;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.util.TextClamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** RAG 检索问句改写（LANGUAGE 模型 + 提示词；失败回退原文）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryRewriteService {

    private static final int MAX_RETRIEVAL_QUERY_CHARS = 200;
    private static final int MIN_LEN_FOR_LLM_REWRITE = 8;

    private static final Pattern FILLER_PREFIX =
            Pattern.compile(
                    "^(请|帮我|帮忙|能否|可以|麻烦|我想|我要|想知道|请问|告诉我|说说|介绍一下|查一下|搜索一下|检索|查找|在知识库|知识库里|根据文档|根据资料)+");

    private final SysLlmModelRepository llmModelRepository;
    private final ModelInvokePort modelInvokePort;
    private final PromptTemplateResolvePort promptTemplates;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    public String rewriteForRetrieval(
            TenantSnapshot snap,
            String userQueryPlaintext,
            List<ModelChatRequest.MessageTurn> recentHistory,
            long conversationId,
            RagRetrievalTuningRuntime tuning) {
        String original = userQueryPlaintext == null ? "" : userQueryPlaintext.trim();
        if (original.isEmpty()) {
            return "";
        }
        List<ModelChatRequest.MessageTurn> history =
                recentHistory == null ? List.of() : List.copyOf(recentHistory);
        boolean hasHistory = !history.isEmpty();
        if (original.length() < MIN_LEN_FOR_LLM_REWRITE && !hasHistory) {
            return clampQuery(original);
        }
        long tenantId = snap.getTenantId();
        SysLlmModel lang = resolveRewriteLanguageModel(tenantId, tuning);
        if (lang != null) {
            try {
                String llmOut =
                        invokeRewriteLlm(
                                tenantId, lang, original, history, conversationId);
                String cleaned = sanitizeLlmOutput(llmOut);
                if (!cleaned.isBlank()) {
                    return cleaned;
                }
            } catch (Exception e) {
                log.warn(
                        "[知识库检索] 问句改写失败，回退原文：租户 {}，原因 {}",
                        tenantId,
                        e.toString());
            }
        }
        return clampQuery(heuristicRewrite(original));
    }

    private SysLlmModel resolveRewriteLanguageModel(long tenantId, RagRetrievalTuningRuntime tuning) {
        String configuredId = tuning.rewriteModelId();
        if (configuredId != null && !configuredId.isBlank()) {
            try {
                long id = Long.parseLong(configuredId.trim());
                SysLlmModel row = llmModelRepository.findById(tenantId, id).orElse(null);
                SysLlmModel usable = usableLanguageModel(row);
                if (usable != null) {
                    return usable;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        var webRewrite = tenantRuntimeSettingApplicationService.webSearchQueryRewriteModelId(tenantId);
        if (webRewrite.isPresent()) {
            SysLlmModel row = llmModelRepository.findById(tenantId, webRewrite.get()).orElse(null);
            if (row != null && row.getModelKind() == LlmModelKind.LANGUAGE) {
                SysLlmModel usable = usableLanguageModel(row);
                if (usable != null) {
                    return usable;
                }
            }
        }
        return llmModelRepository.pickDefaultLanguageModel(tenantId).map(RagQueryRewriteService::usableLanguageModel).orElse(null);
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

    private String invokeRewriteLlm(
            long tenantId,
            SysLlmModel lang,
            String original,
            List<ModelChatRequest.MessageTurn> recentHistory,
            long conversationId)
            throws Exception {
        List<ModelChatRequest.MessageTurn> messages = buildRewriteMessages(tenantId, original, recentHistory);
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(lang.getAlias());
        req.setThinkingEnabled(false);
        req.setConversationId(conversationId > 0L ? conversationId : null);
        req.setUsageScene(LlmUsageScene.RAG_QUERY_REWRITE.getCode());
        req.setMessages(messages);
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return acc.toString();
    }

    private List<ModelChatRequest.MessageTurn> buildRewriteMessages(
            long tenantId, String original, List<ModelChatRequest.MessageTurn> recentHistory) {
        LocalDate today = LocalDate.now();
        String historyBlock = formatHistoryForPrompt(recentHistory);
        Map<String, String> vars =
                Map.of(
                        "user_message",
                        original,
                        "conversation_history",
                        historyBlock,
                        "today",
                        today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        "year",
                        String.valueOf(today.getYear()));
        String system =
                promptTemplates.renderSystem("rag_query_rewrite_system", tenantId, "zh-CN", vars);
        String user = promptTemplates.renderUser("rag_query_rewrite_user", tenantId, "zh-CN", vars);
        var messages = new ArrayList<ModelChatRequest.MessageTurn>();
        var sysTurn = new ModelChatRequest.MessageTurn();
        sysTurn.setRole("system");
        sysTurn.setContent(system);
        messages.add(sysTurn);
        var userTurn = new ModelChatRequest.MessageTurn();
        userTurn.setRole("user");
        userTurn.setContent(user);
        messages.add(userTurn);
        return List.copyOf(messages);
    }

    private static String formatHistoryForPrompt(List<ModelChatRequest.MessageTurn> recentHistory) {
        if (recentHistory == null || recentHistory.isEmpty()) {
            return "（无）";
        }
        var sb = new StringBuilder();
        for (ModelChatRequest.MessageTurn t : recentHistory) {
            if (t == null || t.getRole() == null) {
                continue;
            }
            String body = t.getContent() == null ? "" : t.getContent().trim();
            if (body.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(t.getRole()).append(": ").append(body);
        }
        return sb.isEmpty() ? "（无）" : sb.toString();
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
        return clampQuery(t);
    }

    static String clampQuery(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return TextClamp.ellipsis(s.trim(), MAX_RETRIEVAL_QUERY_CHARS);
    }

    static String clipForLog(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ');
        return t.length() <= 120 ? t : t.substring(0, 120) + "…";
    }
}
