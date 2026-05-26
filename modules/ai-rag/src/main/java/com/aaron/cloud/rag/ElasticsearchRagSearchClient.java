package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * RAG 混合检索中的 Elasticsearch 关键词分支；索引文档需含 {@code tenant_id}、{@code kb_id}、{@code content} 等字段（与
 * {@code ai.rag.elasticsearch.index-name} 一致）。连接与鉴权见 {@code ai.rag.elasticsearch.*}，与 Actuator
 * {@code management.health.elasticsearch} 无关。
 */
@Slf4j
@Component
@Conditional(ElasticsearchRagClientEnabledCondition.class)
public class ElasticsearchRagSearchClient {

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> MAP_DOC_CLASS =
            (Class<Map<String, Object>>) (Class<?>) Map.class;

    private final ElasticsearchClient client;
    private final RestClient lowLevel;
    private final String indexName;
    private final double minScore;

    public ElasticsearchRagSearchClient(AiRagProperties aiRagProperties) {
        var es = aiRagProperties.getElasticsearch();
        this.indexName = es.getIndexName() == null || es.getIndexName().isBlank() ? "rag_agent_documents" : es.getIndexName();
        this.minScore = es.getMinScore() > 0d ? es.getMinScore() : 1.0d;
        String effective = ElasticsearchRagHostParser.resolveEffectiveUriString(es);
        if (effective.isEmpty()) {
            throw new IllegalStateException(
                    "ai.rag.elasticsearch.enabled=true 但未解析到任何节点：请配置 ai.rag.elasticsearch.config.host-ports（与对端同形）");
        }
        List<HttpHost> hosts = ElasticsearchRagHostParser.parseHttpHosts(effective);
        if (hosts.isEmpty()) {
            throw new IllegalStateException(
                    "ai.rag.elasticsearch.enabled=true 但 config.host-ports 未能解析为有效节点: " + effective);
        }
        // RestClient 绑定 Apache HttpClient 4，须使用 org.apache.http.HttpHost。
        RestClientBuilder builder = RestClient.builder(hosts.toArray(HttpHost[]::new));
        String user = resolveEsUsername(es);
        String pass = resolveEsPassword(es);
        if (!user.isEmpty()) {
            var creds = new BasicCredentialsProvider();
            creds.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, pass));
            builder.setHttpClientConfigCallback(
                    (HttpAsyncClientBuilder httpClientBuilder) ->
                            httpClientBuilder.setDefaultCredentialsProvider(creds));
        }
        this.lowLevel = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(lowLevel, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
        log.info(
                "Elasticsearch RAG client index={} nodes={} basicAuth={} minScore={}",
                indexName,
                hosts.stream().map(HttpHost::toString).toList(),
                !user.isEmpty(),
                minScore);
    }

    private static String resolveEsUsername(AiRagProperties.Elasticsearch es) {
        if (es.getUsername() != null && !es.getUsername().isBlank()) {
            return es.getUsername().trim();
        }
        if (es.getConfig() != null
                && es.getConfig().getUserName() != null
                && !es.getConfig().getUserName().isBlank()) {
            return es.getConfig().getUserName().trim();
        }
        return "";
    }

    private static String resolveEsPassword(AiRagProperties.Elasticsearch es) {
        if (es.getUsername() != null && !es.getUsername().isBlank()) {
            return es.getPassword() == null ? "" : es.getPassword();
        }
        if (es.getConfig() != null
                && es.getConfig().getUserName() != null
                && !es.getConfig().getUserName().isBlank()) {
            return es.getConfig().getPassword() == null ? "" : es.getConfig().getPassword();
        }
        return "";
    }

    /** 混合检索引用侧：索引建议含 chunk_id、document_id、title、content；缺 chunk_id 的命中跳过。 */
    public List<RagCitationHit> searchCitationHits(long tenantId, long kbId, String query, int topK) {
        return searchScoredCitationHits(tenantId, kbId, query, topK).stream()
                .map(ElasticsearchScoredCitationHit::citation)
                .toList();
    }

    /**
     * 混合检索引用侧（带 BM25 分）：{@code match} 使用 {@code operator=and}，并应用 {@code min_score}，降低「整句任一词命中」的噪声。
     */
    public List<ElasticsearchScoredCitationHit> searchScoredCitationHits(
            long tenantId, long kbId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int size = (int) Math.min(Math.max(1, topK), 50L);
        double scoreFloor = effectiveMinScore(query);
        try {
            SearchResponse<Map<String, Object>> resp =
                    client.search(
                            s ->
                                    s.index(indexName)
                                            .size(size)
                                            .minScore(scoreFloor)
                                            .query(q -> q.bool(b -> keywordBoolQuery(b, tenantId, kbId, query))),
                            MAP_DOC_CLASS);
            List<ElasticsearchScoredCitationHit> out = new ArrayList<>();
            for (var hit : resp.hits().hits()) {
                Map<String, Object> src = hit.source();
                if (src == null) {
                    continue;
                }
                long chunkId = longFromSource(src, "chunk_id");
                if (chunkId <= 0) {
                    continue;
                }
                long docId = longFromSource(src, "document_id");
                String title = src.get("title") == null ? "" : Objects.toString(src.get("title"), "");
                Object c = src.get("content");
                String preview = previewText(c == null ? "" : Objects.toString(c, ""));
                double score = hit.score() == null ? 0d : hit.score();
                out.add(
                        new ElasticsearchScoredCitationHit(
                                new RagCitationHit(kbId, docId, title, chunkId, 0, preview), score));
            }
            return out;
        } catch (Exception ex) {
            log.warn(
                    "Elasticsearch RAG citation search failed tenantId={} kbId={} index={}",
                    tenantId,
                    kbId,
                    indexName,
                    ex);
            return List.of();
        }
    }

    /**
     * 短查询（单词/短语）BM25 分往往低于全局 {@code min-score}，放宽下限以便混合检索在向量未过阈值时仍能关键词兜底。
     */
    private double effectiveMinScore(String query) {
        if (minScore <= 0d) {
            return 0d;
        }
        String q = query == null ? "" : query.trim();
        if (q.length() <= 16 && !q.contains(" ")) {
            return Math.min(minScore, 0.35d);
        }
        return minScore;
    }

    private static co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder keywordBoolQuery(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b,
            long tenantId,
            long kbId,
            String query) {
        b.must(m -> m.term(t -> t.field("tenant_id").value(tenantId)));
        b.must(m -> m.term(t -> t.field("kb_id").value(kbId)));
        b.must(
                m ->
                        m.multiMatch(
                                mm ->
                                        mm.fields("title^2", "content")
                                                .query(query)
                                                .operator(Operator.And)));
        return b;
    }

    private static long longFromSource(Map<String, Object> src, String key) {
        Object v = src.get(key);
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(Objects.toString(v, "0").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String previewText(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replace('\n', ' ').trim();
        if (t.length() > 240) {
            return t.substring(0, 240) + "…";
        }
        return t;
    }

    /** 混合检索入库：与 {@link #searchCitationHits} 字段约定一致。 */
    public void indexChunks(
            long tenantId, long kbId, long documentId, String title, List<ChunkIndexRow> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String docTitle = title == null ? "" : title.trim();
        for (ChunkIndexRow row : chunks) {
            if (row.chunkId() <= 0 || row.content() == null || row.content().isBlank()) {
                continue;
            }
            String id = tenantId + "_" + kbId + "_" + row.chunkId();
            Map<String, Object> doc =
                    Map.of(
                            "tenant_id",
                            tenantId,
                            "kb_id",
                            kbId,
                            "chunk_id",
                            row.chunkId(),
                            "document_id",
                            documentId,
                            "title",
                            docTitle,
                            "content",
                            row.content());
            try {
                client.index(i -> i.index(indexName).id(id).document(doc));
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Elasticsearch index chunk failed index="
                                + indexName
                                + " chunkId="
                                + row.chunkId(),
                        ex);
            }
        }
        log.debug(
                "Elasticsearch indexed chunks tenantId={} kbId={} docId={} n={}",
                tenantId,
                kbId,
                documentId,
                chunks.size());
    }

    public record ChunkIndexRow(long chunkId, String content) {}

    public List<String> searchContents(long tenantId, long kbId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int size = (int) Math.min(Math.max(1, topK), 50L);
        double scoreFloor = effectiveMinScore(query);
        try {
            SearchResponse<Map<String, Object>> resp =
                    client.search(
                            s ->
                                    s.index(indexName)
                                            .size(size)
                                            .minScore(scoreFloor)
                                            .query(q -> q.bool(b -> keywordBoolQuery(b, tenantId, kbId, query))),
                            MAP_DOC_CLASS);
            List<String> out = new ArrayList<>();
            for (var hit : resp.hits().hits()) {
                Map<String, Object> src = hit.source();
                if (src == null) {
                    continue;
                }
                Object c = src.get("content");
                if (c != null) {
                    String text = Objects.toString(c, "").trim();
                    if (!text.isEmpty()) {
                        out.add(text);
                    }
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn(
                    "Elasticsearch RAG search failed tenantId={} kbId={} index={}",
                    tenantId,
                    kbId,
                    indexName,
                    ex);
            return List.of();
        }
    }

    @PreDestroy
    public void close() {
        try {
            lowLevel.close();
        } catch (Exception e) {
            log.warn("Elasticsearch RestClient close failed", e);
        }
    }
}
