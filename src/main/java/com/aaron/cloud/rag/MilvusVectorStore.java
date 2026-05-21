package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagVectorRecallHit;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.remoting.EurekaInfraAddress;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

/**
 * Milvus 向量写入与检索；collection 名与编排层约定为 {@code kb_{kbId}}，字段 {@code chunk_ref}（VarChar PK）、{@code
 * embedding}（FloatVector）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.providers.vector-store", havingValue = "milvus")
public class MilvusVectorStore implements VectorStorePort {

    public static final String FIELD_CHUNK_REF = "chunk_ref";
    public static final String FIELD_EMBEDDING = "embedding";

    private final MilvusClientV2 client;
    private final AiProvidersProperties properties;
    /** 已创建并已 load 的 collection 名。 */
    private final ConcurrentHashMap.KeySetView<String, Boolean> loadedCollections = ConcurrentHashMap.newKeySet();

    public MilvusVectorStore(AiProvidersProperties properties, ObjectProvider<DiscoveryClient> discoveryClient) {
        this.properties = properties;
        var m = properties.getMilvus();
        String uri = resolveUri(properties, discoveryClient.getIfAvailable());
        var builder = ConnectConfig.builder().uri(uri).dbName(m.getDatabase());
        if (m.getToken() != null && !m.getToken().isBlank()) {
            builder.token(m.getToken());
        }
        builder.secure(m.isSecure());
        this.client = new MilvusClientV2(builder.build());
        log.info("Milvus SDK connected uri={} db={}", uri, m.getDatabase());
    }

    private static String resolveUri(AiProvidersProperties properties, DiscoveryClient discoveryClient) {
        var m = properties.getMilvus();
        if (properties.isInfraViaDiscovery() && discoveryClient != null) {
            var fromEureka = EurekaInfraAddress.pickHttpBaseUri(discoveryClient, m.getServiceId());
            if (fromEureka.isPresent()) {
                log.info("Milvus 使用 Eureka 服务 [{}] 地址: {}", m.getServiceId(), fromEureka.get());
                return fromEureka.get();
            }
            log.warn(
                    "Milvus 已开启 infra-via-discovery，但 Eureka 中无服务 [{}]，回退到 host/port: {}:{}",
                    m.getServiceId(),
                    m.getHost(),
                    m.getPort());
        }
        return "http://" + m.getHost() + ":" + m.getPort();
    }

    private int dim() {
        int d = properties.getMilvus().getVectorDimension();
        return d > 0 ? d : RagQueryEmbeddingHasher.DEFAULT_DIM;
    }

    private void ensureCollectionLoaded(String collectionName) {
        if (loadedCollections.contains(collectionName)) {
            return;
        }
        synchronized (this) {
            if (loadedCollections.contains(collectionName)) {
                return;
            }
            int d = dim();
            Boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(collectionName).build());
            if (!Boolean.TRUE.equals(exists)) {
                CreateCollectionReq.CollectionSchema schema = client.createSchema();
                schema.addField(
                        AddFieldReq.builder()
                                .fieldName(FIELD_CHUNK_REF)
                                .dataType(DataType.VarChar)
                                .maxLength(96)
                                .isPrimaryKey(true)
                                .build());
                schema.addField(
                        AddFieldReq.builder()
                                .fieldName(FIELD_EMBEDDING)
                                .dataType(DataType.FloatVector)
                                .dimension(d)
                                .build());
                IndexParam indexParam =
                        IndexParam.builder()
                                .fieldName(FIELD_EMBEDDING)
                                .indexType(IndexParam.IndexType.AUTOINDEX)
                                .metricType(IndexParam.MetricType.COSINE)
                                .build();
                CreateCollectionReq createReq =
                        CreateCollectionReq.builder()
                                .collectionName(collectionName)
                                .collectionSchema(schema)
                                .indexParams(Collections.singletonList(indexParam))
                                .build();
                client.createCollection(createReq);
                log.info("Milvus 已创建 collection={} dim={} metric=COSINE", collectionName, d);
            }
            client.loadCollection(LoadCollectionReq.builder().collectionName(collectionName).build());
            loadedCollections.add(collectionName);
        }
    }

    @Override
    public void upsertChunks(long tenantId, String collection, List<String> chunkRefs, List<float[]> vectors) {
        if (chunkRefs == null || chunkRefs.isEmpty()) {
            return;
        }
        if (vectors == null || vectors.size() != chunkRefs.size()) {
            log.warn(
                    "Milvus upsertChunks skipped sizeMismatch tenantId={} collection={} refs={} vecs={}",
                    tenantId,
                    collection,
                    chunkRefs.size(),
                    vectors == null ? -1 : vectors.size());
            return;
        }
        try {
            ensureCollectionLoaded(collection);
            deleteChunkVectors(tenantId, collection, chunkRefs);
            List<JsonObject> rows = new ArrayList<>();
            for (int i = 0; i < chunkRefs.size(); i++) {
                String ref = chunkRefs.get(i);
                float[] vec = vectors.get(i);
                if (ref == null || ref.isBlank() || vec == null || vec.length != dim()) {
                    log.warn("Milvus upsertChunks skip invalid row refBlank={} vecLen={}", ref == null, vec == null ? -1 : vec.length);
                    continue;
                }
                JsonObject row = new JsonObject();
                row.addProperty(FIELD_CHUNK_REF, ref);
                JsonArray arr = new JsonArray();
                for (float v : vec) {
                    arr.add(v);
                }
                row.add(FIELD_EMBEDDING, arr);
                rows.add(row);
            }
            if (rows.isEmpty()) {
                throw new IllegalStateException(
                        "Milvus upsert produced no valid rows (check embedding dimension vs collection): "
                                + collection);
            }
            client.insert(InsertReq.builder().collectionName(collection).data(rows).build());
            log.debug("Milvus insert rows={} collection={}", rows.size(), collection);
        } catch (Exception e) {
            log.error("Milvus upsertChunks failed tenantId={} collection={}", tenantId, collection, e);
            throw new IllegalStateException("Milvus upsert failed collection=" + collection, e);
        }
    }

    @Override
    public List<RagVectorRecallHit> searchVectors(long tenantId, String collection, float[] queryVector, int topK) {
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        if (queryVector.length != dim()) {
            log.warn(
                    "Milvus searchVectors skipped dimMismatch collection={} queryDim={} expected={}",
                    collection,
                    queryVector.length,
                    dim());
            return List.of();
        }
        try {
            ensureCollectionLoaded(collection);
            int k = (int) Math.min(Math.max(1, topK), 50L);
            SearchResp resp =
                    client.search(
                            SearchReq.builder()
                                    .collectionName(collection)
                                    .data(Collections.singletonList(new FloatVec(queryVector)))
                                    .annsField(FIELD_EMBEDDING)
                                    .topK(k)
                                    .outputFields(List.of(FIELD_CHUNK_REF))
                                    .build());
            List<RagVectorRecallHit> out = new ArrayList<>();
            if (resp.getSearchResults() == null) {
                return out;
            }
            for (List<SearchResp.SearchResult> group : resp.getSearchResults()) {
                if (group == null) {
                    continue;
                }
                for (SearchResp.SearchResult r : group) {
                    if (r == null) {
                        continue;
                    }
                    String ref = null;
                    if (r.getEntity() != null && r.getEntity().get(FIELD_CHUNK_REF) != null) {
                        ref = Objects.toString(r.getEntity().get(FIELD_CHUNK_REF), "").trim();
                    }
                    if (ref == null || ref.isEmpty()) {
                        continue;
                    }
                    float score = r.getScore() == null ? 0f : r.getScore();
                    out.add(new RagVectorRecallHit(ref, score));
                }
            }
            return out;
        } catch (Exception e) {
            log.error("Milvus searchVectors failed tenantId={} collection={}", tenantId, collection, e);
            return List.of();
        }
    }

    @Override
    public void deleteChunkVectors(long tenantId, String collection, List<String> embeddingRefs) {
        if (embeddingRefs == null || embeddingRefs.isEmpty()) {
            return;
        }
        try {
            if (!Boolean.TRUE.equals(
                    client.hasCollection(HasCollectionReq.builder().collectionName(collection).build()))) {
                return;
            }
            List<String> valid =
                    embeddingRefs.stream()
                            .filter(r -> r != null && !r.isBlank())
                            .map(String::trim)
                            .distinct()
                            .toList();
            if (valid.isEmpty()) {
                return;
            }
            String inList =
                    valid.stream()
                            .map(r -> "\"" + r.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                            .collect(Collectors.joining(","));
            String expr = FIELD_CHUNK_REF + " in [" + inList + "]";
            client.delete(DeleteReq.builder().collectionName(collection).filter(expr).build());
        } catch (Exception e) {
            log.warn("Milvus deleteChunkVectors failed tenantId={} collection={}", tenantId, collection, e);
        }
    }

    @PreDestroy
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Milvus client close failed", e);
        }
    }
}
