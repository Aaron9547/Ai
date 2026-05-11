package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagCitationHit;
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

    public ElasticsearchRagSearchClient(AiRagProperties aiRagProperties) {
        var es = aiRagProperties.getElasticsearch();
        this.indexName = es.getIndexName() == null || es.getIndexName().isBlank() ? "ai_rag_chunk" : es.getIndexName();
        String effective = ElasticsearchRagHostParser.resolveEffectiveUriString(es);
        if (effective.isEmpty()) {
            throw new IllegalStateException(
                    "ai.rag.elasticsearch.enabled=true 但未解析到任何节点：请配置 ai.rag.elasticsearch.config.host-ports（与 ly-ai-rag-svc 同形）");
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
                "Elasticsearch RAG client index={} nodes={} basicAuth={}",
                indexName,
                hosts.stream().map(HttpHost::toString).toList(),
                !user.isEmpty());
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
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int size = (int) Math.min(Math.max(1, topK), 50L);
        try {
            SearchResponse<Map<String, Object>> resp =
                    client.search(
                            s ->
                                    s.index(indexName)
                                            .size(size)
                                            .query(
                                                    q ->
                                                            q.bool(
                                                                    b -> {
                                                                        b.must(
                                                                                m ->
                                                                                        m.term(
                                                                                                t ->
                                                                                                        t.field(
                                                                                                                        "tenant_id")
                                                                                                                .value(
                                                                                                                        tenantId)));
                                                                        b.must(
                                                                                m ->
                                                                                        m.term(
                                                                                                t ->
                                                                                                        t.field("kb_id")
                                                                                                                .value(
                                                                                                                        kbId)));
                                                                        b.must(
                                                                                m ->
                                                                                        m.match(
                                                                                                mm ->
                                                                                                        mm.field(
                                                                                                                        "content")
                                                                                                                .query(
                                                                                                                        query)));
                                                                        return b;
                                                                    })),
                            MAP_DOC_CLASS);
            List<RagCitationHit> out = new ArrayList<>();
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
                out.add(new RagCitationHit(kbId, docId, title, chunkId, 0, preview));
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

    public List<String> searchContents(long tenantId, long kbId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int size = (int) Math.min(Math.max(1, topK), 50L);
        try {
            SearchResponse<Map<String, Object>> resp =
                    client.search(
                            s ->
                                    s.index(indexName)
                                            .size(size)
                                            .query(
                                                    q ->
                                                            q.bool(
                                                                    b -> {
                                                                        b.must(
                                                                                m ->
                                                                                        m.term(
                                                                                                t ->
                                                                                                        t.field(
                                                                                                                        "tenant_id")
                                                                                                                .value(
                                                                                                                        tenantId)));
                                                                        b.must(
                                                                                m ->
                                                                                        m.term(
                                                                                                t ->
                                                                                                        t.field("kb_id")
                                                                                                                .value(
                                                                                                                        kbId)));
                                                                        b.must(
                                                                                m ->
                                                                                        m.match(
                                                                                                mm ->
                                                                                                        mm.field(
                                                                                                                        "content")
                                                                                                                .query(
                                                                                                                        query)));
                                                                        return b;
                                                                    })),
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
