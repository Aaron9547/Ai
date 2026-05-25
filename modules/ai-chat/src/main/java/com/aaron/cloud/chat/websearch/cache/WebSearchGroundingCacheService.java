package com.aaron.cloud.chat.websearch.cache;

import com.aaron.cloud.chat.websearch.WebGroundingBundle;
import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.api.enums.chat.WebSearchCacheTier;
import com.aaron.cloud.common.tenant.runtime.WebSearchGroundingCachePolicy;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 联网检索 Redis 缓存：精确键 + 语义近邻（租户 VECTOR 嵌入）；滚动 fresh/warm/stale 小时档位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchGroundingCacheService {

    private static final String ENTRY_PREFIX = "ai:ws-g:e:";
    private static final String INDEX_PREFIX = "ai:ws-g:idx:";
    private static final Duration REDIS_TTL = Duration.ofHours(72);

    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final RagEmbeddingPort ragEmbeddingPort;

    public WebSearchGroundingCachePolicy policy(long tenantId) {
        return tenantRuntimeSettingApplicationService.webSearchGroundingCachePolicy(tenantId);
    }

    public String configScopeHash(int configuredRounds, List<String> suffixes) {
        StringBuilder sb = new StringBuilder();
        sb.append(configuredRounds).append('|');
        if (suffixes != null) {
            for (String s : suffixes) {
                sb.append(s == null ? "" : s).append('\u0001');
            }
        }
        return WebSearchQueryNormalizer.sha256Hex(sb.toString()).substring(0, 16);
    }

    public Optional<WebSearchGroundingCacheLookup> lookup(
            long tenantId,
            long webSearchModelId,
            String configScope,
            String normalizedQuery,
            WebSearchGroundingCachePolicy policy) {
        if (!policy.enabled() || normalizedQuery.isBlank()) {
            return Optional.empty();
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return Optional.empty();
        }
        String exactHash = WebSearchQueryNormalizer.sha256Hex(normalizedQuery);
        String entryKey = entryRedisKey(tenantId, webSearchModelId, configScope, exactHash);
        Optional<CacheEntry> exact = readEntry(redis, entryKey);
        if (exact.isPresent()) {
            return toLookup(exact.get(), policy, false);
        }
        String indexKey = indexRedisKey(tenantId, webSearchModelId, configScope);
        try {
            String indexJson = redis.opsForValue().get(indexKey);
            if (indexJson == null || indexJson.isBlank()) {
                return Optional.empty();
            }
            JsonNode arr = objectMapper.readTree(indexJson);
            if (!arr.isArray()) {
                return Optional.empty();
            }
            for (JsonNode n : arr) {
                if (n == null || !n.isObject()) {
                    continue;
                }
                String norm = n.path("normalizedQuery").asText(null);
                if (norm == null || !norm.equals(normalizedQuery)) {
                    continue;
                }
                String refKey = n.path("entryKey").asText(null);
                if (refKey == null || refKey.isBlank()) {
                    continue;
                }
                Optional<CacheEntry> cand = readEntry(redis, refKey);
                if (cand.isPresent()) {
                    log.info("[联网缓存] 索引规范化问句命中：租户 {}，模型 {}", tenantId, webSearchModelId);
                    return toLookup(cand.get(), policy, false);
                }
            }
            if (!policy.semanticEnabled()) {
                return Optional.empty();
            }
            float[] queryVec = embedQuery(tenantId, normalizedQuery);
            if (queryVec == null || queryVec.length == 0) {
                return Optional.empty();
            }
            double best = 0.0;
            CacheEntry bestEntry = null;
            for (JsonNode n : arr) {
                if (n == null || !n.isObject()) {
                    continue;
                }
                String refKey = n.path("entryKey").asText(null);
                if (refKey == null || refKey.isBlank()) {
                    continue;
                }
                Optional<CacheEntry> cand = readEntry(redis, refKey);
                if (cand.isEmpty()) {
                    continue;
                }
                CacheEntry e = cand.get();
                if (e.embedding == null || e.embedding.length != queryVec.length) {
                    continue;
                }
                double sim = WebSearchVectorSimilarity.cosine(queryVec, e.embedding);
                if (sim >= policy.similarityThreshold() && sim > best) {
                    best = sim;
                    bestEntry = e;
                }
            }
            if (bestEntry != null) {
                log.info(
                        "[联网缓存] 语义近邻命中：租户 {}，模型 {}，sim={}",
                        tenantId,
                        webSearchModelId,
                        String.format("%.3f", best));
                return toLookup(bestEntry, policy, true);
            }
        } catch (Exception ex) {
            log.warn("[联网缓存] 语义索引扫描失败：租户 {}", tenantId, ex);
        }
        return Optional.empty();
    }

    public void store(
            long tenantId,
            long webSearchModelId,
            String configScope,
            String normalizedQuery,
            WebGroundingBundle bundle,
            WebSearchGroundingCachePolicy policy) {
        if (!policy.enabled() || normalizedQuery.isBlank() || bundle == null) {
            return;
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        String exactHash = WebSearchQueryNormalizer.sha256Hex(normalizedQuery);
        String entryKey = entryRedisKey(tenantId, webSearchModelId, configScope, exactHash);
        float[] embedding = null;
        if (policy.semanticEnabled()) {
            embedding = embedQuery(tenantId, normalizedQuery);
        }
        long now = System.currentTimeMillis();
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("fetchedAtEpochMs", now);
            root.put("normalizedQuery", normalizedQuery);
            root.put("summaryText", bundle.summaryText() == null ? "" : bundle.summaryText());
            ArrayNode refs = root.putArray("references");
            writeReferences(refs, bundle.references());
            if (embedding != null && embedding.length > 0) {
                ArrayNode emb = root.putArray("embedding");
                for (float v : embedding) {
                    emb.add(v);
                }
            }
            redis.opsForValue().set(entryKey, objectMapper.writeValueAsString(root), REDIS_TTL);
            if (policy.semanticEnabled() && embedding != null) {
                appendIndex(
                        redis,
                        tenantId,
                        webSearchModelId,
                        configScope,
                        entryKey,
                        normalizedQuery,
                        embedding,
                        policy.indexMaxEntries());
            }
        } catch (Exception ex) {
            log.warn("[联网缓存] 写入失败：租户 {}，key {}", tenantId, entryKey, ex);
        }
    }

    private Optional<WebSearchGroundingCacheLookup> toLookup(
            CacheEntry entry, WebSearchGroundingCachePolicy policy, boolean semanticNearMatch) {
        long ageMs = System.currentTimeMillis() - entry.fetchedAtEpochMs;
        WebSearchCacheTier tier = policy.tierForAgeMs(ageMs);
        if (tier == WebSearchCacheTier.EXPIRED) {
            return Optional.empty();
        }
        WebGroundingBundle bundle =
                new WebGroundingBundle(
                        entry.summaryText == null ? "" : entry.summaryText,
                        entry.references == null ? List.of() : entry.references);
        return Optional.of(new WebSearchGroundingCacheLookup(tier, bundle, entry.fetchedAtEpochMs, semanticNearMatch));
    }

    private float[] embedQuery(long tenantId, String normalizedQuery) {
        Long vectorModelId = tenantRuntimeSettingApplicationService.memoryEmbeddingVectorModelId(tenantId).orElse(null);
        return ragEmbeddingPort.embedByVectorModelIdOrHash(tenantId, vectorModelId, normalizedQuery);
    }

    private void appendIndex(
            StringRedisTemplate redis,
            long tenantId,
            long webSearchModelId,
            String configScope,
            String entryKey,
            String normalizedQuery,
            float[] embedding,
            int maxEntries)
            throws Exception {
        String indexKey = indexRedisKey(tenantId, webSearchModelId, configScope);
        int cap = Math.clamp(maxEntries, 10, 500);
        ArrayNode arr;
        String raw = redis.opsForValue().get(indexKey);
        if (raw == null || raw.isBlank()) {
            arr = objectMapper.createArrayNode();
        } else {
            JsonNode parsed = objectMapper.readTree(raw);
            arr = parsed.isArray() ? (ArrayNode) parsed.deepCopy() : objectMapper.createArrayNode();
        }
        Iterator<JsonNode> it = arr.iterator();
        while (it.hasNext()) {
            JsonNode n = it.next();
            if (entryKey.equals(n.path("entryKey").asText(null))) {
                it.remove();
            }
        }
        ObjectNode row = objectMapper.createObjectNode();
        row.put("entryKey", entryKey);
        row.put("normalizedQuery", normalizedQuery == null ? "" : normalizedQuery);
        row.put("fetchedAtEpochMs", System.currentTimeMillis());
        ArrayNode emb = row.putArray("embedding");
        for (float v : embedding) {
            emb.add(v);
        }
        arr.insert(0, row);
        while (arr.size() > cap) {
            arr.remove(arr.size() - 1);
        }
        redis.opsForValue().set(indexKey, objectMapper.writeValueAsString(arr), REDIS_TTL);
    }

    private Optional<CacheEntry> readEntry(StringRedisTemplate redis, String entryKey) {
        try {
            String json = redis.opsForValue().get(entryKey);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(json);
            long fetchedAt = root.path("fetchedAtEpochMs").asLong(0L);
            if (fetchedAt <= 0L) {
                return Optional.empty();
            }
            String summary = root.path("summaryText").asText("");
            List<WebSearchReference> refs = readReferences(root.path("references"));
            float[] embedding = readEmbedding(root.path("embedding"));
            return Optional.of(new CacheEntry(fetchedAt, summary, refs, embedding));
        } catch (Exception ex) {
            log.debug("[联网缓存] 读取条目失败 key={}", entryKey, ex);
            return Optional.empty();
        }
    }

    private static List<WebSearchReference> readReferences(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return List.of();
        }
        List<WebSearchReference> out = new ArrayList<>();
        for (JsonNode c : arr) {
            if (c == null || !c.isObject()) {
                continue;
            }
            String summary = c.path("summary").asText("");
            if (summary.isEmpty()) {
                summary = c.path("snippet").asText("");
            }
            out.add(
                    new WebSearchReference(
                            c.path("title").asText(""),
                            c.path("url").asText(""),
                            summary,
                            textOrNull(c, "siteName"),
                            textOrNull(c, "logoUrl"),
                            textOrNull(c, "publishTime"),
                            textOrNull(c, "extraJson")));
        }
        return List.copyOf(out);
    }

    private static float[] readEmbedding(JsonNode emb) {
        if (emb == null || !emb.isArray() || emb.isEmpty()) {
            return null;
        }
        float[] v = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) {
            v[i] = (float) emb.get(i).asDouble(0.0);
        }
        return v;
    }

    private void writeReferences(ArrayNode arr, List<WebSearchReference> refs) {
        if (refs == null) {
            return;
        }
        for (WebSearchReference r : refs) {
            if (r == null) {
                continue;
            }
            ObjectNode o = arr.addObject();
            o.put("title", r.title() != null ? r.title() : "");
            o.put("url", r.url() != null ? r.url() : "");
            o.put("summary", r.snippet() != null ? r.snippet() : "");
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
        }
    }

    private static String textOrNull(JsonNode c, String field) {
        if (!c.has(field) || c.get(field).isNull()) {
            return null;
        }
        String t = c.get(field).asText(null);
        return t == null || t.isBlank() ? null : t;
    }

    private static String entryRedisKey(long tenantId, long webSearchModelId, String configScope, String exactHash) {
        return ENTRY_PREFIX + tenantId + ":" + webSearchModelId + ":" + configScope + ":" + exactHash;
    }

    private static String indexRedisKey(long tenantId, long webSearchModelId, String configScope) {
        return INDEX_PREFIX + tenantId + ":" + webSearchModelId + ":" + configScope;
    }

    private record CacheEntry(
            long fetchedAtEpochMs, String summaryText, List<WebSearchReference> references, float[] embedding) {}
}
