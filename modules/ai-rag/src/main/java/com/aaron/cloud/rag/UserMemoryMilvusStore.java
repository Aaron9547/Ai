package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.ports.UserMemoryVectorPort;
import com.aaron.cloud.common.config.properties.AiMemoryProperties;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.remoting.EurekaInfraAddress;
import com.aaron.cloud.rag.runtime.TenantRagRuntimeResolver;
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
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * 用户记忆具体层专用 Milvus：标量 {@code tenant_id + subject_key} 过滤，主键为 MySQL {@code ten_user_memory_chunk.id}。
 */
@Slf4j
@Component
@Conditional(OnUserMemoryMilvusEnabled.class)
public class UserMemoryMilvusStore implements UserMemoryVectorPort {

    public static final String FIELD_CHUNK_ID = "chunk_id";
    public static final String FIELD_TENANT_ID = "tenant_id";
    public static final String FIELD_SUBJECT_KEY = "subject_key";
    public static final String FIELD_EMBEDDING = "embedding";

    private final MilvusClientV2 client;
    private final AiProvidersProperties providersProperties;
    private final AiMemoryProperties memoryProperties;
    private final TenantRagRuntimeResolver tenantRagRuntimeResolver;
    private final ConcurrentHashMap.KeySetView<String, Boolean> loadedCollections = ConcurrentHashMap.newKeySet();

    public UserMemoryMilvusStore(
            AiProvidersProperties providersProperties,
            AiMemoryProperties memoryProperties,
            TenantRagRuntimeResolver tenantRagRuntimeResolver,
            ObjectProvider<DiscoveryClient> discoveryClient) {
        this.providersProperties = providersProperties;
        this.memoryProperties = memoryProperties;
        this.tenantRagRuntimeResolver = tenantRagRuntimeResolver;
        var m = providersProperties.getMilvus();
        String uri = resolveUri(providersProperties, discoveryClient.getIfAvailable());
        var builder = ConnectConfig.builder().uri(uri).dbName(m.getDatabase());
        if (m.getToken() != null && !m.getToken().isBlank()) {
            builder.token(m.getToken());
        }
        builder.secure(m.isSecure());
        this.client = new MilvusClientV2(builder.build());
        String baseColl =
                memoryProperties.getMilvusCollection() == null || memoryProperties.getMilvusCollection().isBlank()
                        ? "user_memory_chunk"
                        : memoryProperties.getMilvusCollection().trim();
        log.info(
                "UserMemoryMilvusStore connected uri={} db={} collectionPerTenant={}_t<tenantId>",
                uri,
                m.getDatabase(),
                baseColl);
    }

    private String collectionName(long tenantId) {
        String n = memoryProperties.getMilvusCollection();
        String base = n == null || n.isBlank() ? "user_memory_chunk" : n.trim();
        return base + "_t" + tenantId;
    }

    private int dim(long tenantId) {
        return tenantRagRuntimeResolver.resolveVectorDimension(tenantId);
    }

    private static String resolveUri(AiProvidersProperties properties, DiscoveryClient discoveryClient) {
        var m = properties.getMilvus();
        if (properties.isInfraViaDiscovery() && discoveryClient != null) {
            var fromEureka = EurekaInfraAddress.pickHttpBaseUri(discoveryClient, m.getServiceId());
            if (fromEureka.isPresent()) {
                return fromEureka.get();
            }
        }
        return "http://" + m.getHost() + ":" + m.getPort();
    }

    private void ensureCollectionLoaded(long tenantId) {
        String name = collectionName(tenantId);
        if (loadedCollections.contains(name)) {
            return;
        }
        synchronized (this) {
            if (loadedCollections.contains(name)) {
                return;
            }
            int d = dim(tenantId);
            Boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(name).build());
            if (!Boolean.TRUE.equals(exists)) {
                CreateCollectionReq.CollectionSchema schema = client.createSchema();
                schema.addField(
                        AddFieldReq.builder()
                                .fieldName(FIELD_CHUNK_ID)
                                .dataType(DataType.Int64)
                                .isPrimaryKey(true)
                                .build());
                schema.addField(
                        AddFieldReq.builder()
                                .fieldName(FIELD_TENANT_ID)
                                .dataType(DataType.Int64)
                                .build());
                schema.addField(
                        AddFieldReq.builder()
                                .fieldName(FIELD_SUBJECT_KEY)
                                .dataType(DataType.VarChar)
                                .maxLength(96)
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
                                .collectionName(name)
                                .collectionSchema(schema)
                                .indexParams(Collections.singletonList(indexParam))
                                .build();
                client.createCollection(createReq);
                log.info("UserMemoryMilvusStore created collection={} dim={}", name, d);
            }
            client.loadCollection(LoadCollectionReq.builder().collectionName(name).build());
            loadedCollections.add(name);
        }
    }

    private static String escapeVarchar(String raw) {
        String s = raw == null ? "" : raw;
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String subjectFilter(long tenantId, String subjectKey) {
        return FIELD_TENANT_ID + " == " + tenantId + " && " + FIELD_SUBJECT_KEY + " == " + escapeVarchar(subjectKey);
    }

    public void upsertVector(long tenantId, String subjectKey, long chunkId, float[] vector) {
        int expectedDim = dim(tenantId);
        if (vector == null || vector.length != expectedDim) {
            log.warn(
                    "user memory milvus upsert skip dimMismatch tenantId={} chunkId={} vecLen={} expected={}",
                    tenantId,
                    chunkId,
                    vector == null ? -1 : vector.length,
                    expectedDim);
            return;
        }
        try {
            ensureCollectionLoaded(tenantId);
            String name = collectionName(tenantId);
            String delExpr = FIELD_CHUNK_ID + " == " + chunkId + " && " + subjectFilter(tenantId, subjectKey);
            client.delete(DeleteReq.builder().collectionName(name).filter(delExpr).build());
            JsonObject row = new JsonObject();
            row.addProperty(FIELD_CHUNK_ID, chunkId);
            row.addProperty(FIELD_TENANT_ID, tenantId);
            row.addProperty(FIELD_SUBJECT_KEY, subjectKey);
            JsonArray arr = new JsonArray();
            for (float v : vector) {
                arr.add(v);
            }
            row.add(FIELD_EMBEDDING, arr);
            client.insert(InsertReq.builder().collectionName(name).data(List.of(row)).build());
        } catch (Exception e) {
            log.error("user memory milvus upsert failed tenantId={} chunkId={}", tenantId, chunkId, e);
        }
    }

    /** 按主体删除该租户下所有记忆向量（隐私删除、设备归并清理等）。 */
    public void deleteByTenantAndSubject(long tenantId, String subjectKey) {
        try {
            String name = collectionName(tenantId);
            if (!Boolean.TRUE.equals(
                    client.hasCollection(HasCollectionReq.builder().collectionName(name).build()))) {
                return;
            }
            ensureCollectionLoaded(tenantId);
            String expr = subjectFilter(tenantId, subjectKey);
            client.delete(DeleteReq.builder().collectionName(name).filter(expr).build());
        } catch (Exception e) {
            log.warn("user memory milvus deleteByTenantAndSubject failed tenantId={}", tenantId, e);
        }
    }

    /** 返回按向量相似度排序的 chunk 主键列表。 */
    public List<Long> searchChunkIds(long tenantId, String subjectKey, float[] queryVector, int topK) {
        if (queryVector == null || queryVector.length != dim(tenantId)) {
            return List.of();
        }
        try {
            ensureCollectionLoaded(tenantId);
            String name = collectionName(tenantId);
            int k = (int) Math.min(Math.max(1, topK), 50L);
            SearchResp resp =
                    client.search(
                            SearchReq.builder()
                                    .collectionName(name)
                                    .data(Collections.singletonList(new FloatVec(queryVector)))
                                    .annsField(FIELD_EMBEDDING)
                                    .filter(subjectFilter(tenantId, subjectKey))
                                    .topK(k)
                                    .outputFields(List.of(FIELD_CHUNK_ID))
                                    .build());
            List<Long> out = new ArrayList<>();
            if (resp.getSearchResults() == null) {
                return out;
            }
            for (List<SearchResp.SearchResult> group : resp.getSearchResults()) {
                if (group == null) {
                    continue;
                }
                for (SearchResp.SearchResult r : group) {
                    if (r == null || r.getEntity() == null) {
                        continue;
                    }
                    Object v = r.getEntity().get(FIELD_CHUNK_ID);
                    if (v instanceof Number n) {
                        out.add(n.longValue());
                    } else if (v != null) {
                        try {
                            out.add(Long.parseLong(Objects.toString(v, "").trim()));
                        } catch (NumberFormatException ignored) {
                            // skip
                        }
                    }
                }
            }
            return out;
        } catch (Exception e) {
            log.error("user memory milvus search failed tenantId={}", tenantId, e);
            return List.of();
        }
    }

    @PreDestroy
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("UserMemoryMilvusStore client close failed", e);
        }
    }
}
