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

    private static final String INGEST_SYSTEM =
            """
            你是对话知识沉淀助手。根据本轮用户问题与助手回复，判断是否值得沉淀为一条「知识节点」。
            只输出严格 JSON（不要 markdown），格式：
            {"skip":true}
            或
            {"skip":false,"title":"不超过24字标题","summary":"1～3句摘要","topicTags":["主题分类","子标签1","子标签2"]}
            topicTags 第一项为「知识星球」主题名（如：排序算法、Java、前端工程化），决定星系中的星球；第 2 项起为子标签（技术名、语言、算法名等，便于与历史节点关联）。
            规则：
            1. 若用户消息中给出【已有主题星球】，且本轮属于同一技术领域，topicTags[0] 必须与列表中某一项完全一致，勿为相近话题另造新名（如已有「排序算法」则勿写「Java排序」「算法」）。
            2. 同一对话内的追问、换语言实现、对比、延伸（如「五种语言冒泡排序」接在「十大排序」后）应沉淀，skip 仅用于纯寒暄或完全无新信息的重复。
            3. 子标签尽量包含能串联历史节点的关键词（如：排序、冒泡、Java、多语言）。
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
        appendPlanetContext(userPayload, tenantId, subjectKey, conversationId);

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

    private void appendPlanetContext(
            StringBuilder userPayload, long tenantId, String subjectKey, long conversationId) {
        Set<String> planetNames = new LinkedHashSet<>();
        for (TenUserKnowledgeNode n : nodeRepository.listRecent(tenantId, subjectKey, 40)) {
            if (n.getTopicTagsJson() == null || n.getTopicTagsJson().isBlank()) {
                continue;
            }
            try {
                List<String> tags = objectMapper.readValue(n.getTopicTagsJson(), new TypeReference<List<String>>() {});
                if (tags != null && !tags.isEmpty() && tags.getFirst() != null) {
                    String primary = tags.getFirst().trim();
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
