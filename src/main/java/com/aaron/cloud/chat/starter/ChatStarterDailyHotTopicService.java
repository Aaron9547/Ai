package com.aaron.cloud.chat.starter;

import com.aaron.cloud.chat.websearch.ChatWebSearchGroundingService;
import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.ChatStarterDailyBatchStatus;
import com.aaron.cloud.common.api.enums.ChatStarterPromptScene;
import com.aaron.cloud.common.api.enums.ChatStarterPromptSource;
import com.aaron.cloud.common.api.enums.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.chat.ChatStarterDailyBatchRepository;
import com.aaron.cloud.common.chat.ChatStarterPromptRepository;
import com.aaron.cloud.common.chat.entity.ChatStarterDailyBatch;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.time.BeijingTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 通过联网检索 + 语言模型，生成并落库「每日热点」推荐问句（兜底池）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStarterDailyHotTopicService {

    private static final String HOT_SEARCH_QUERY =
            "今日中国网络与社会热点新闻 科技 财经 文化 2026 最新";

    private static final String STRUCTURE_SYSTEM =
            """
            你是推荐问句编辑。根据用户提供的联网检索摘要，输出适合 AI 对话开场白的短问题。
            只输出 JSON 数组，不要 markdown，不要解释。每项为中文问句，长度 8～36 字，共 8～12 条。
            问句应具体、可点击、避免重复。示例：["AIGC 最近有哪些新应用？","如何写一份周报模板？"]
            """;

    private final SysLlmModelRepository llmModelRepository;
    private final ChatWebSearchGroundingService webSearchGroundingService;
    private final ModelInvokePort modelInvokePort;
    private final ChatStarterDailyBatchRepository dailyBatchRepository;
    private final ChatStarterPromptRepository promptRepository;
    private final ChatStarterPromptJsonSupport jsonSupport;

    public void refreshForTenant(long tenantId, boolean force) {
        LocalDate today = BeijingTime.today();
        String batchKey = today.toString().replace("-", "");

        var existing = dailyBatchRepository.findByTenantAndDate(tenantId, today);
        if (!force && existing.isPresent()) {
            var b = existing.get();
            if (b.getStatus() == ChatStarterDailyBatchStatus.OK
                    && b.getQuestionsJson() != null
                    && !b.getQuestionsJson().isBlank()) {
                return;
            }
        }

        ChatStarterDailyBatch batch =
                existing.orElseGet(
                        () -> {
                            var row = new ChatStarterDailyBatch();
                            row.setTenantId(tenantId);
                            row.setTopicDate(today);
                            row.setStatus(ChatStarterDailyBatchStatus.PENDING);
                            dailyBatchRepository.insert(row);
                            return row;
                        });

        if (!llmModelRepository.hasEnabledWebSearchModel(tenantId)) {
            failBatch(batch, "租户未配置启用的联网搜索模型");
            return;
        }

        SysLlmModel webModel =
                llmModelRepository
                        .pickDefaultWebSearchModel(tenantId)
                        .orElse(null);
        if (webModel == null) {
            failBatch(batch, "无可用联网搜索模型");
            return;
        }

        var snap =
                TenantContextHolder.TenantSnapshot.builder().tenantId(tenantId).build();
        TenantContextHolder.set(snap);
        try {
            var grounding =
                    webSearchGroundingService.groundWithRaw(
                            snap, webModel, HOT_SEARCH_QUERY, 0L);
            String summary =
                    grounding.bundle().summaryText() == null
                            ? ""
                            : grounding.bundle().summaryText().trim();
            if (summary.isBlank()) {
                failBatch(batch, "联网检索未返回可用摘要");
                return;
            }

            List<String> questions = structureQuestions(tenantId, summary);
            if (questions.isEmpty()) {
                failBatch(batch, "语言模型未解析出有效问句");
                return;
            }

            batch.setStatus(ChatStarterDailyBatchStatus.OK);
            batch.setQuestionsJson(jsonSupport.toJson(questions));
            batch.setErrorMessage(null);
            batch.setFetchedAt(LocalDateTime.now());
            dailyBatchRepository.updateById(batch);

            promptRepository.deleteHotTopicByTenantExceptBatch(tenantId, batchKey);
            int order = 0;
            for (String q : questions) {
                var p = new ChatStarterPrompt();
                p.setTenantId(tenantId);
                p.setScene(ChatStarterPromptScene.EMPTY);
                p.setSource(ChatStarterPromptSource.HOT_TOPIC_DAILY);
                p.setPromptText(q);
                p.setWeight(90);
                p.setEnabled(1);
                p.setSortOrder(order++);
                p.setBatchKey(batchKey);
                promptRepository.insert(p);
            }
            log.info("[推荐问题] 每日热点已刷新 tenantId={} count={}", tenantId, questions.size());
        } catch (Exception e) {
            log.warn("[推荐问题] 每日热点刷新失败 tenantId={}", tenantId, e);
            failBatch(batch, truncate(e.getMessage(), 480));
        } finally {
            TenantContextHolder.clear();
        }
    }

    private List<String> structureQuestions(long tenantId, String summary) throws Exception {
        SysLlmModel lang =
                llmModelRepository.pickDefaultLanguageModel(tenantId).orElse(null);
        if (lang == null) {
            return List.of();
        }
        LlmModelKind k = lang.getModelKind() != null ? lang.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.LANGUAGE) {
            return List.of();
        }
        try {
            LlmModelKindPolicy.assertLanguageModelForChatStream(lang);
        } catch (Exception ex) {
            log.debug("[推荐问题] 跳过非对话语言模型 tenantId={}", tenantId);
            return List.of();
        }

        var sys = new ModelChatRequest.MessageTurn();
        sys.setRole("system");
        sys.setContent(STRUCTURE_SYSTEM);
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        user.setContent("【联网检索摘要】\n" + summary);
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(lang.getAlias());
        req.setThinkingEnabled(false);
        req.setMessages(List.of(sys, user));
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return jsonSupport.parseQuestions(acc.toString());
    }

    private void failBatch(ChatStarterDailyBatch batch, String message) {
        batch.setStatus(ChatStarterDailyBatchStatus.FAILED);
        batch.setErrorMessage(message);
        batch.setFetchedAt(LocalDateTime.now());
        dailyBatchRepository.updateById(batch);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
