package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetSubjectQuerySupport;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTopicTagsNormalizer;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.knowledgeplanet.TenUserKnowledgeNodeRepository;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.TenUserMemoryAbstractRepository;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.profile.UserProfileApplicationService;
import com.aaron.cloud.common.util.TextClamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePlanetIngestService {

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final KnowledgePlanetLlmSupport llmSupport;
    private final KnowledgePlanetSubjectQuerySupport subjectQuerySupport;
    private final TenUserKnowledgeNodeRepository nodeRepository;
    private final UserProfileApplicationService userProfileApplicationService;
    private final TenUserMemoryAbstractRepository memoryAbstractRepository;
    private final ObjectMapper objectMapper;
    private final PromptTemplateResolvePort promptTemplates;

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
        subjectQuerySupport.mergeGuestNodesIfNeeded(snap);
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
        appendPlanetContext(userPayload, tenantId, subjectKey, conversationId);

        String raw =
                llmSupport.invokeJson(
                        tenantId,
                        model.get(),
                        promptTemplates.resolveSystem("planet_ingest_system", tenantId, "zh-CN"),
                        userPayload.toString());
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
            List<String> normalized =
                    KnowledgePlanetTopicTagsNormalizer.normalize(parsed.getTopicTags());
            if (!normalized.isEmpty()) {
                row.setTopicTagsJson(objectMapper.writeValueAsString(normalized));
            }
        }
        nodeRepository.insert(row);
        log.debug("[知识星球] 节点已沉淀 tenantId={} subject={} title={}", tenantId, subjectKey, title);
    }

    private void appendPlanetContext(
            StringBuilder userPayload, long tenantId, String subjectKey, long conversationId) {
        Set<String> planetNames = new LinkedHashSet<>();
        for (TenUserKnowledgeNode n : nodeRepository.listRecent(tenantId, subjectKey, 40)) {
            if (n.getTopicTagsJson() == null || n.getTopicTagsJson().isBlank()) {
                continue;
            }
            try {
                List<String> tags = objectMapper.readValue(n.getTopicTagsJson(), new TypeReference<List<String>>() {});
                List<String> normalized = KnowledgePlanetTopicTagsNormalizer.normalize(tags);
                if (!normalized.isEmpty()) {
                    String primary = normalized.getFirst().trim();
                    if (!primary.isEmpty()) {
                        planetNames.add(primary);
                    }
                }
            } catch (Exception ignored) {
                // skip malformed
            }
        }
        if (!planetNames.isEmpty()) {
            userPayload.append("\n\n【已有主题星球】（topicTags[0] 请优先复用）\n");
            userPayload.append(String.join("、", planetNames));
        }
        List<TenUserKnowledgeNode> inConv =
                nodeRepository.listByConversation(tenantId, subjectKey, conversationId, 8);
        if (!inConv.isEmpty()) {
            userPayload.append("\n\n【本会话已沉淀】\n");
            for (TenUserKnowledgeNode n : inConv) {
                userPayload.append("- ").append(n.getTitle());
                if (n.getTopicTagsJson() != null && !n.getTopicTagsJson().isBlank()) {
                    userPayload.append(" （标签：").append(n.getTopicTagsJson()).append("）");
                }
                userPayload.append('\n');
            }
        }
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
