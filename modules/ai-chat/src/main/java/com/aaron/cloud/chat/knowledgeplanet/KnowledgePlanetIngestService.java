package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.common.context.TenantContextHolder.TenantSnapshot;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.knowledgeplanet.TenUserKnowledgeNodeRepository;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.TenUserMemoryAbstractRepository;
import com.aaron.cloud.common.profile.UserProfileApplicationService;
import com.aaron.cloud.common.util.TextClamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePlanetIngestService {

    private static final String INGEST_SYSTEM =
            """
            你是对话知识沉淀助手。根据本轮用户问题与助手回复，判断是否值得沉淀为一条「知识节点」。
            只输出严格 JSON（不要 markdown），格式：
            {"skip":true}
            或
            {"skip":false,"title":"不超过24字标题","summary":"1～3句摘要","topicTags":["主题分类","子标签"]}
            topicTags 第一项为「知识星球」主题名（如：内网穿透、前端工程化），决定星系中的星球；其余为子标签。
            无实质信息、纯寒暄、重复已有话题时 skip 为 true。
            """;

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final KnowledgePlanetLlmSupport llmSupport;
    private final TenUserKnowledgeNodeRepository nodeRepository;
    private final UserProfileApplicationService userProfileApplicationService;
    private final TenUserMemoryAbstractRepository memoryAbstractRepository;
    private final ObjectMapper objectMapper;

    public void scheduleAfterTurn(
            TenantSnapshot snap,
            long conversationId,
            long assistantMessageId,
            String userQuestion,
            String assistantText,
            String modelAlias) {
        if (!planetRuntime.isEnabled(snap.getTenantId())) {
            return;
        }
        Thread.startVirtualThread(
                () -> {
                    try {
                        ingestTurn(
                                snap,
                                conversationId,
                                assistantMessageId,
                                userQuestion,
                                assistantText,
                                modelAlias);
                    } catch (Exception ex) {
                        log.warn(
                                "[知识星球] 沉淀失败 tenantId={} conversationId={}",
                                snap.getTenantId(),
                                conversationId,
                                ex);
                    }
                });
    }

    private void ingestTurn(
            TenantSnapshot snap,
            long conversationId,
            long assistantMessageId,
            String userQuestion,
            String assistantText,
            String modelAlias)
            throws Exception {
        String subjectKey = ProfileSubjectKey.fromSnapshot(snap);
        if (subjectKey == null) {
            return;
        }
        String userQ = userQuestion == null ? "" : userQuestion.trim();
        String asst = assistantText == null ? "" : assistantText.trim();
        if (userQ.isEmpty() && asst.isEmpty()) {
            return;
        }
        long tenantId = snap.getTenantId();
        Optional<SysLlmModel> model = llmSupport.resolveLanguageModel(tenantId, modelAlias);
        if (model.isEmpty()) {
            return;
        }

        String profile =
                userProfileApplicationService.buildPromptAddendum(snap, userQ, false);
        String abstractHint =
                memoryAbstractRepository
                        .findByTenantAndSubject(tenantId, subjectKey)
                        .map(a -> a.getBodyJson() == null ? "" : a.getBodyJson())
                        .orElse("");

        StringBuilder userPayload = new StringBuilder();
        userPayload.append("【用户问题】\n").append(TextClamp.ellipsis(userQ, 4000));
        userPayload.append("\n\n【助手回复】\n").append(TextClamp.ellipsis(asst, 8000));
        if (!profile.isBlank()) {
            userPayload.append("\n\n【画像摘要】\n").append(profile);
        }
        if (!abstractHint.isBlank()) {
            userPayload.append("\n\n【长期记忆抽象】\n").append(TextClamp.ellipsis(abstractHint, 2000));
        }

        String raw = llmSupport.invokeJson(tenantId, model.get(), INGEST_SYSTEM, userPayload.toString());
        IngestLlmResult parsed = llmSupport.parseJson(raw, IngestLlmResult.class);
        if (parsed == null || parsed.isSkip()) {
            return;
        }
        String title = parsed.getTitle() == null ? "" : parsed.getTitle().trim();
        String summary = parsed.getSummary() == null ? "" : parsed.getSummary().trim();
        if (title.isEmpty() || summary.isEmpty()) {
            return;
        }

        var row = new TenUserKnowledgeNode();
        row.setTenantId(tenantId);
        row.setSubjectKey(subjectKey);
        row.setConversationId(conversationId);
        row.setMessageId(assistantMessageId);
        row.setTitle(TextClamp.ellipsis(title, 255));
        row.setSummary(TextClamp.ellipsis(summary, 2000));
        if (parsed.getTopicTags() != null && !parsed.getTopicTags().isEmpty()) {
            row.setTopicTagsJson(objectMapper.writeValueAsString(parsed.getTopicTags()));
        }
        nodeRepository.insert(row);
        log.debug("[知识星球] 节点已沉淀 tenantId={} subject={} title={}", tenantId, subjectKey, title);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class IngestLlmResult {
        private boolean skip = true;
        private String title;
        private String summary;
        private List<String> topicTags;
    }
}
