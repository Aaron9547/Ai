package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.config.properties.AiMemoryProperties;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.api.enums.infra.VectorStoreProviderMode;
import com.aaron.cloud.common.context.TenantContextHolder.TenantSnapshot;
import com.aaron.cloud.common.profile.entity.TenUserMemoryChunk;
import com.aaron.cloud.common.profile.memory.MemoryAbstractAsyncPublisher;
import com.aaron.cloud.common.profile.memory.MemoryAbstractRefreshMessage;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.util.TextClamp;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.api.ports.UserMemoryVectorPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 方案三之「具体层」增量片段 + 「抽象层」由异步 LLM（RocketMQ / Redis 队列）刷新；Milvus 可选承载向量检索，失败时 LIKE 降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryApplicationService {

    private static final int SNIPPET_MAX = 1800;

    private final TenUserMemoryChunkRepository chunkRepository;
    private final TenUserMemoryAbstractRepository abstractRepository;
    private final RagEmbeddingPort ragEmbeddingPort;
    private final AiMemoryProperties aiMemoryProperties;
    private final AiProvidersProperties aiProvidersProperties;
    private final ObjectProvider<UserMemoryVectorPort> userMemoryVectorPort;
    private final MemoryAbstractAsyncPublisher memoryAbstractAsyncPublisher;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    public void afterUserUtterance(
            TenantSnapshot snap, String utterance, Long conversationIdOrNull, String modelAliasOrNull) {
        insertUtteranceChunk(snap, utterance, conversationIdOrNull, MemoryChunkRoles.USER, modelAliasOrNull, "user");
    }

    /** 助手流式落库成功后写入「助手侧」记忆片段，形成双线事件级记忆。 */
    public void afterAssistantUtterance(
            TenantSnapshot snap, String assistantText, Long conversationIdOrNull, String modelAliasOrNull) {
        insertUtteranceChunk(
                snap, assistantText, conversationIdOrNull, MemoryChunkRoles.ASSISTANT, modelAliasOrNull, "assistant");
    }

    /** 用户点击画像推荐资讯；失败仅打日志，不抛出。 */
    public void afterInterestNewsClick(TenantSnapshot snap, String title, String tag, String url) {
        StringBuilder sb = new StringBuilder();
        if (tag != null && !tag.isBlank()) {
            sb.append('[').append(tag.trim()).append("] ");
        }
        if (title != null && !title.isBlank()) {
            sb.append(title.trim());
        }
        if (url != null && !url.isBlank()) {
            sb.append(" ").append(url.trim());
        }
        insertUtteranceChunk(snap, sb.toString(), null, MemoryChunkRoles.INTEREST_NEWS, null, "interest_news");
    }

    private void insertUtteranceChunk(
            TenantSnapshot snap,
            String text,
            Long conversationIdOrNull,
            String chunkRole,
            String modelAliasOrNull,
            String abstractTrigger) {
        String subjectKey = ProfileSubjectKey.fromSnapshot(snap);
        if (subjectKey == null || text == null || text.isBlank()) {
            return;
        }
        long tenantId = snap.getTenantId();
        String snippet = normalizeSnippet(text);
        if (snippet.isEmpty()) {
            return;
        }
        try {
            var row = new TenUserMemoryChunk();
            row.setTenantId(tenantId);
            row.setSubjectKey(subjectKey);
            row.setConversationId(conversationIdOrNull);
            row.setChunkRole(chunkRole);
            row.setContentSnippet(snippet);
            chunkRepository.insert(row);
            indexMilvusChunk(tenantId, subjectKey, row);
            publishAbstractRefresh(tenantId, subjectKey, modelAliasOrNull, abstractTrigger);
        } catch (Exception ex) {
            log.warn("memory chunk insert failed tenantId={} subject={}", tenantId, subjectKey, ex);
        }
    }

    /**
     * 设备主体归并到用户后：删除旧设备向量分区，并对用户主体全量重建向量索引（chunk 主键不变、subject 已更新）。
     */
    public void repairMilvusAfterGuestMerge(long tenantId, String deviceSubjectKey, String userSubjectKey) {
        userMemoryVectorPort.ifAvailable(s -> s.deleteByTenantAndSubject(tenantId, deviceSubjectKey));
        reindexMilvusForSubject(tenantId, userSubjectKey);
    }

    public void reindexMilvusForSubject(long tenantId, String subjectKey) {
        if (!milvusMemoryActive()) {
            return;
        }
        UserMemoryVectorPort store = userMemoryVectorPort.getIfAvailable();
        if (store == null) {
            return;
        }
        Long mid = resolveMemoryEmbeddingVectorModelId(tenantId);
        long pageNo = 1;
        while (true) {
            var page = chunkRepository.pageByTenantAndSubject(tenantId, subjectKey, pageNo, 200);
            if (page.getRecords().isEmpty()) {
                break;
            }
            for (TenUserMemoryChunk c : page.getRecords()) {
                try {
                    float[] v =
                            ragEmbeddingPort.embedByVectorModelIdOrHash(
                                    tenantId, mid, c.getContentSnippet() == null ? "" : c.getContentSnippet());
                    store.upsertVector(tenantId, subjectKey, c.getId(), v);
                    chunkRepository.updateVectorRef(c.getId(), "milvus");
                } catch (Exception ex) {
                    log.warn(
                            "memory milvus reindex row failed tenantId={} chunkId={}",
                            tenantId,
                            c.getId(),
                            ex);
                }
            }
            if (pageNo >= page.getPages()) {
                break;
            }
            pageNo++;
        }
    }

    /**
     * 供对话 system 追加：抽象层 JSON + 与当前输入相关的具体层片段（Milvus 向量优先，无命中则 LIKE / 最近片段）。
     *
     * @param includeConcreteRecall false 时仅注入抽象层（新会话首轮避免旧问句摘录触发「您问过多次」类表述）
     */
    public String buildMemoryPromptSection(
            TenantSnapshot snap, String currentUserTextForRecall, boolean includeConcreteRecall) {
        String subjectKey = ProfileSubjectKey.fromSnapshot(snap);
        if (subjectKey == null) {
            return "";
        }
        long tenantId = snap.getTenantId();
        var j = new StringJoiner("\n");
        var memPol = tenantRuntimeSettingApplicationService.memoryPolicy(tenantId);
        abstractRepository
                .findByTenantAndSubject(tenantId, subjectKey)
                .filter(a -> a.getBodyJson() != null && !a.getBodyJson().isBlank())
                .ifPresent(
                        a ->
                                j.add(
                                        "【长期记忆·抽象】\n"
                                                + TextClamp.ellipsis(
                                                        a.getBodyJson(), memPol.resolvedPromptAbstractBodyMaxChars())));
        List<TenUserMemoryChunk> hits =
                includeConcreteRecall
                        ? recallConcreteChunks(tenantId, subjectKey, currentUserTextForRecall)
                        : List.of();
        if (!hits.isEmpty()) {
            j.add("【长期记忆·摘录】");
            int lineCap = memPol.resolvedPromptConcreteChunkMaxChars();
            for (TenUserMemoryChunk c : hits) {
                String role = c.getChunkRole() == null ? "USER" : c.getChunkRole();
                String sn = c.getContentSnippet() == null ? "" : c.getContentSnippet();
                j.add("- [" + role + "] " + TextClamp.ellipsis(sn, lineCap));
            }
        }
        String s = j.toString();
        return s.isBlank() ? "" : s + "\n\n";
    }

    public void deleteAllForSubject(long tenantId, String subjectKey) {
        userMemoryVectorPort.ifAvailable(s -> s.deleteByTenantAndSubject(tenantId, subjectKey));
        chunkRepository.deleteByTenantAndSubject(tenantId, subjectKey);
        abstractRepository.deleteByTenantAndSubject(tenantId, subjectKey);
    }

    private List<TenUserMemoryChunk> recallConcreteChunks(long tenantId, String subjectKey, String query) {
        var memPol = tenantRuntimeSettingApplicationService.memoryPolicy(tenantId);
        int limit = memPol.resolvedPromptConcreteChunkLimit();
        LinkedHashMap<Long, TenUserMemoryChunk> ordered = new LinkedHashMap<>();
        if (milvusMemoryActive() && query != null && !query.isBlank()) {
            UserMemoryVectorPort store = userMemoryVectorPort.getIfAvailable();
            if (store != null) {
                Long mid = resolveMemoryEmbeddingVectorModelId(tenantId);
                float[] qv =
                        ragEmbeddingPort.embedByVectorModelIdOrHash(
                                tenantId, mid, query.trim());
                List<Long> ids =
                        store.searchChunkIds(
                                tenantId,
                                subjectKey,
                                qv,
                                Math.max(limit, memPol.resolvedVectorSearchTopK()));
                if (!ids.isEmpty()) {
                    List<TenUserMemoryChunk> rows = chunkRepository.listByIds(tenantId, subjectKey, ids);
                    var byId = new LinkedHashMap<Long, TenUserMemoryChunk>();
                    for (TenUserMemoryChunk r : rows) {
                        byId.put(r.getId(), r);
                    }
                    for (Long id : ids) {
                        TenUserMemoryChunk c = byId.get(id);
                        if (c != null) {
                            ordered.putIfAbsent(c.getId(), c);
                        }
                    }
                }
            }
        }
        if (ordered.size() < limit) {
            List<TenUserMemoryChunk> lexical =
                    chunkRepository.searchLexical(tenantId, subjectKey, query == null ? "" : query, limit);
            for (TenUserMemoryChunk c : lexical) {
                ordered.putIfAbsent(c.getId(), c);
                if (ordered.size() >= limit) {
                    break;
                }
            }
        }
        return new ArrayList<>(ordered.values());
    }

    private void indexMilvusChunk(long tenantId, String subjectKey, TenUserMemoryChunk row) {
        if (!milvusMemoryActive() || row.getId() == null) {
            return;
        }
        UserMemoryVectorPort store = userMemoryVectorPort.getIfAvailable();
        if (store == null) {
            return;
        }
        try {
            Long mid = resolveMemoryEmbeddingVectorModelId(tenantId);
            float[] v =
                    ragEmbeddingPort.embedByVectorModelIdOrHash(
                            tenantId, mid, row.getContentSnippet() == null ? "" : row.getContentSnippet());
            store.upsertVector(tenantId, subjectKey, row.getId(), v);
            chunkRepository.updateVectorRef(row.getId(), "milvus");
        } catch (Exception ex) {
            log.warn("memory milvus index failed tenantId={} chunkId={}", tenantId, row.getId(), ex);
        }
    }

    private boolean milvusMemoryActive() {
        return aiMemoryProperties.isVectorEnabled()
                && aiProvidersProperties.resolvedVectorStore() == VectorStoreProviderMode.milvus;
    }

    private void publishAbstractRefresh(
            long tenantId, String subjectKey, String modelAliasOrNull, String trigger) {
        memoryAbstractAsyncPublisher.publish(
                new MemoryAbstractRefreshMessage(tenantId, subjectKey, trimOrNull(modelAliasOrNull), trigger));
    }

    /** 来自租户运行时 {@code MEMORY_EMBEDDING_VECTOR_MODEL_ID}（管理端「用户画像」页配置）。 */
    private Long resolveMemoryEmbeddingVectorModelId(long tenantId) {
        return tenantRuntimeSettingApplicationService.memoryEmbeddingVectorModelId(tenantId).orElse(null);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeSnippet(String utterance) {
        String s = utterance.strip().replace('\n', ' ').replace('\r', ' ');
        if (s.length() > SNIPPET_MAX) {
            return s.substring(0, SNIPPET_MAX);
        }
        return s;
    }
}
