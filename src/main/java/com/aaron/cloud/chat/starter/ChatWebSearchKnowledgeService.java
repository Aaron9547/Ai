package com.aaron.cloud.chat.starter;

import com.aaron.cloud.chat.websearch.WebGroundingBundle;
import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.aaron.cloud.chat.websearch.WebSearchSummarySupport;
import com.aaron.cloud.chat.websearch.cache.WebSearchGroundingMergeSupport;
import com.aaron.cloud.chat.websearch.cache.WebSearchQueryNormalizer;
import com.aaron.cloud.chat.websearch.cache.WebSearchVectorSimilarity;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptScene;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptSource;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.chat.ChatStarterPromptRepository;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.tenant.runtime.WebSearchGroundingCachePolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 将成功的联网检索沉淀到 {@code chat_starter_prompt}（scene=WEB_KNOWLEDGE），
 * 供管理端「联网知识库」维护，并在后续相似问句时优先本地命中以减少外呼。
 *
 * <p>{@link ChatStarterPromptSource} 区分对话沉淀、今日智能洞察、每日热点等来源，便于运营辨认。
 *
 * <p>同一问句允许多版本：{@code freshHours} 内合并更新同一条；超过后外呼产生的新结果插入新行，
 * 以便资讯更新后仍能保留历史并在命中时取最新有效版本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatWebSearchKnowledgeService {

    private static final int PROMPT_TEXT_MAX = 256;
    /** 知识库摘要仅用引用要点列表，不存 Ark 整篇回答。 */
    private static final int KNOWLEDGE_SUMMARY_MAX_BULLETS = 12;
    private static final int SEMANTIC_SCAN_CAP = 200;
    private static final double SEMANTIC_MIN_SCORE = 0.82;

    private final ChatStarterPromptRepository promptRepository;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final RagEmbeddingPort ragEmbeddingPort;
    private final ObjectMapper objectMapper;

    /**
     * 外呼前尝试命中租户联网知识库（精确问句优先，其次 VECTOR 语义近邻）。
     * 仅使用未超过 {@link WebSearchGroundingCachePolicy#staleHours()} 的最新版本。
     */
    public Optional<WebGroundingBundle> tryLookup(long tenantId, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return Optional.empty();
        }
        WebSearchGroundingCachePolicy policy =
                tenantRuntimeSettingApplicationService.webSearchGroundingCachePolicy(tenantId);
        String hash = WebSearchQueryNormalizer.sha256Hex(normalizedQuery);

        Optional<ChatStarterPrompt> exact =
                promptRepository.findLatestWebKnowledgeByHash(tenantId, hash, true);
        if (exact.isPresent() && isWithinStale(exact.get(), policy)) {
            bumpHit(exact.get());
            return parseBundle(exact.get().getGroundingJson());
        }
        return semanticLookup(tenantId, normalizedQuery, policy);
    }

    /** 联网检索成功后写入或刷新知识库条目（异步虚拟线程，不阻塞主对话）。 */
    public void ingestAsync(
            long tenantId,
            String rawQuery,
            String normalizedQuery,
            WebGroundingBundle bundle,
            ChatStarterPromptSource source) {
        if (bundle == null || !hasContent(bundle) || normalizedQuery.isBlank() || source == null) {
            return;
        }
        String raw = rawQuery == null ? "" : rawQuery.trim();
        String norm = normalizedQuery;
        WebGroundingBundle copy = bundle;
        ChatStarterPromptSource src = source;
        Thread.startVirtualThread(
                () -> {
                    try {
                        ingestSync(tenantId, raw, norm, copy, src);
                    } catch (Exception ex) {
                        log.warn("[联网知识库] 沉淀失败 tenantId={} q={}", tenantId, norm, ex);
                    }
                });
    }

    void ingestSync(
            long tenantId,
            String rawQuery,
            String normalizedQuery,
            WebGroundingBundle bundle,
            ChatStarterPromptSource source) {
        if (bundle == null || !hasContent(bundle) || normalizedQuery.isBlank()) {
            return;
        }
        WebSearchGroundingCachePolicy policy =
                tenantRuntimeSettingApplicationService.webSearchGroundingCachePolicy(tenantId);
        String hash = WebSearchQueryNormalizer.sha256Hex(normalizedQuery);
        String json = writeGroundingJson(bundleForKnowledgePersistence(bundle));
        String promptText = truncatePromptText(rawQuery, normalizedQuery);
        int refs = bundle.references() == null ? 0 : bundle.references().size();
        int weight = Math.min(200, 80 + refs * 5);

        Optional<ChatStarterPrompt> latest =
                promptRepository.findLatestWebKnowledgeByHash(tenantId, hash, false);
        if (latest.isPresent() && isWithinFresh(latest.get(), policy)) {
            ChatStarterPrompt row = latest.get();
            row.setPromptText(promptText);
            row.setQueryNormalized(normalizedQuery);
            row.setQueryNormHash(hash);
            row.setGroundingJson(json);
            row.setEnabled(1);
            row.setWeight(weight);
            promptRepository.updateById(row, tenantId);
            log.debug("[联网知识库] 刷新当前版本 tenantId={} id={}", tenantId, row.getId());
            return;
        }

        insertNewRow(tenantId, promptText, normalizedQuery, hash, json, weight, source);
        if (latest.isPresent()) {
            log.info(
                    "[联网知识库] 新增版本 tenantId={} hash={} priorId={}",
                    tenantId,
                    hash.substring(0, 8),
                    latest.get().getId());
        } else {
            log.debug("[联网知识库] 首条沉淀 tenantId={} hash={}", tenantId, hash.substring(0, 8));
        }
    }

    private void insertNewRow(
            long tenantId,
            String promptText,
            String normalizedQuery,
            String hash,
            String json,
            int weight,
            ChatStarterPromptSource source) {
        var row = new ChatStarterPrompt();
        row.setTenantId(tenantId);
        row.setScene(ChatStarterPromptScene.WEB_KNOWLEDGE);
        row.setSource(source);
        row.setPromptText(promptText);
        row.setQueryNormalized(normalizedQuery);
        row.setQueryNormHash(hash);
        row.setGroundingJson(json);
        row.setHitCount(0);
        row.setWeight(weight);
        row.setEnabled(1);
        row.setRequireWebSearch(1);
        row.setSortOrder(0);
        promptRepository.insert(row);
    }

    private Optional<WebGroundingBundle> semanticLookup(
            long tenantId, String normalizedQuery, WebSearchGroundingCachePolicy policy) {
        Optional<Long> vectorModelId =
                tenantRuntimeSettingApplicationService.memoryEmbeddingVectorModelId(tenantId);
        if (vectorModelId.isEmpty()) {
            return Optional.empty();
        }
        float[] queryVec;
        try {
            queryVec =
                    ragEmbeddingPort.embedByVectorModelIdOrHash(
                            tenantId, vectorModelId.get(), normalizedQuery);
        } catch (Exception ex) {
            log.debug("[联网知识库] 问句向量化失败 tenantId={}", tenantId, ex);
            return Optional.empty();
        }
        if (queryVec == null || queryVec.length == 0) {
            return Optional.empty();
        }

        List<ChatStarterPrompt> pool =
                promptRepository.listWebKnowledgeForSemanticScan(tenantId, SEMANTIC_SCAN_CAP);
        Set<String> seenHashes = new HashSet<>();
        double best = 0.0;
        ChatStarterPrompt bestRow = null;
        for (ChatStarterPrompt p : pool) {
            if (!isWithinStale(p, policy)) {
                continue;
            }
            String h = p.getQueryNormHash();
            if (h != null && !h.isBlank() && !seenHashes.add(h)) {
                continue;
            }
            String text = p.getQueryNormalized();
            if (text == null || text.isBlank()) {
                continue;
            }
            try {
                float[] pVec =
                        ragEmbeddingPort.embedByVectorModelIdOrHash(
                                tenantId, vectorModelId.get(), text);
                double sim = WebSearchVectorSimilarity.cosine(queryVec, pVec);
                if (sim >= SEMANTIC_MIN_SCORE && sim > best) {
                    best = sim;
                    bestRow = p;
                }
            } catch (Exception ignored) {
                // skip row
            }
        }
        if (bestRow == null) {
            return Optional.empty();
        }
        log.info(
                "[联网知识库] 语义命中 tenantId={} sim={} stored={}",
                tenantId,
                String.format("%.3f", best),
                bestRow.getQueryNormalized());
        bumpHit(bestRow);
        return parseBundle(bestRow.getGroundingJson());
    }

    private static boolean isWithinFresh(ChatStarterPrompt row, WebSearchGroundingCachePolicy policy) {
        return ageMs(row) <= hoursToMs(policy.freshHours());
    }

    private static boolean isWithinStale(ChatStarterPrompt row, WebSearchGroundingCachePolicy policy) {
        return ageMs(row) <= hoursToMs(policy.staleHours());
    }

    private static long ageMs(ChatStarterPrompt row) {
        LocalDateTime at = row.getUpdatedAt() != null ? row.getUpdatedAt() : row.getCreatedAt();
        if (at == null) {
            return Long.MAX_VALUE;
        }
        return Duration.between(at, LocalDateTime.now()).toMillis();
    }

    private static long hoursToMs(int hours) {
        return Math.max(0, hours) * 3_600_000L;
    }

    private void bumpHit(ChatStarterPrompt row) {
        if (row.getId() == null || row.getTenantId() == null) {
            return;
        }
        promptRepository.incrementHitCount(row.getId(), row.getTenantId());
    }

    private static boolean hasContent(WebGroundingBundle bundle) {
        String s = bundle.summaryText();
        if (s != null && !s.isBlank()) {
            return true;
        }
        return bundle.references() != null && !bundle.references().isEmpty();
    }

    private static String truncatePromptText(String rawQuery, String normalizedQuery) {
        String t = rawQuery == null || rawQuery.isBlank() ? normalizedQuery : rawQuery.trim();
        if (t.length() <= PROMPT_TEXT_MAX) {
            return t;
        }
        return t.substring(0, PROMPT_TEXT_MAX);
    }

    /**
     * 联网知识库只沉淀「规范化问句 + 检索引用」；摘要用引用要点列表，不写入火山 Ark 的整段生成文。
     * 对话主流程仍使用外呼返回的完整 {@link WebGroundingBundle}。
     */
    static WebGroundingBundle bundleForKnowledgePersistence(WebGroundingBundle live) {
        if (live == null) {
            return new WebGroundingBundle("", List.of());
        }
        List<WebSearchReference> refs =
                WebSearchGroundingMergeSupport.dedupeReferences(live.references());
        String summary =
                WebSearchSummarySupport.bulletsFromReferences(
                        refs, KNOWLEDGE_SUMMARY_MAX_BULLETS);
        return new WebGroundingBundle(summary == null ? "" : summary, refs);
    }

    private String writeGroundingJson(WebGroundingBundle bundle) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            String summary =
                    WebSearchSummarySupport.joinSummaryPieces(
                            WebSearchSummarySupport.splitSummaryPieces(bundle.summaryText()));
            root.put("summaryText", summary);
            ArrayNode refs = root.putArray("references");
            if (bundle.references() != null) {
                for (WebSearchReference r : bundle.references()) {
                    ObjectNode o = refs.addObject();
                    o.put("title", r.title() == null ? "" : r.title());
                    o.put("url", r.url() == null ? "" : r.url());
                    o.put("snippet", r.snippet() == null ? "" : r.snippet());
                    if (r.siteName() != null) {
                        o.put("siteName", r.siteName());
                    }
                    if (r.logoUrl() != null) {
                        o.put("logoUrl", r.logoUrl());
                    }
                    if (r.publishTime() != null) {
                        o.put("publishTime", r.publishTime());
                    }
                    if (r.extraJson() != null) {
                        o.put("extraJson", r.extraJson());
                    }
                    if (r.sourceKey() != null) {
                        o.put("sourceKey", r.sourceKey());
                    }
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("grounding json write failed", e);
        }
    }

    private Optional<WebGroundingBundle> parseBundle(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String summary = root.path("summaryText").asText("");
            List<WebSearchReference> refs = new ArrayList<>();
            JsonNode arr = root.path("references");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    refs.add(
                            new WebSearchReference(
                                    n.path("title").asText(""),
                                    n.path("url").asText(""),
                                    n.path("snippet").asText(""),
                                    textOrNull(n, "siteName"),
                                    textOrNull(n, "logoUrl"),
                                    textOrNull(n, "publishTime"),
                                    textOrNull(n, "extraJson"),
                                    textOrNull(n, "sourceKey")));
                }
            }
            if (summary.isBlank() && refs.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new WebGroundingBundle(summary, List.copyOf(refs)));
        } catch (Exception ex) {
            log.warn("[联网知识库] JSON 解析失败", ex);
            return Optional.empty();
        }
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }
}
