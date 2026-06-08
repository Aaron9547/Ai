package com.aaron.cloud.rag.ltr;

import com.aaron.cloud.common.filemeta.FileObjectMetaRepository;
import com.aaron.cloud.common.filemeta.entity.FileObjectMeta;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;

/** 从 {@code file_object_meta} + 对象存储加载 LTR 权重；缺失时用 builtin。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagLtrModelStore {

    private final FileObjectMetaRepository fileObjectMetaRepository;
    private final ObjectProvider<MinioClient> minioClient;
    private final ObjectMapper objectMapper;

    private final Map<Long, CachedWeights> cache = new ConcurrentHashMap<>();

    public double[] resolveWeights(long tenantId, RagRetrievalTuningRuntime tuning) {
        Long fileId = tuning.activeLtrFileObjectId();
        if (fileId == null || fileId <= 0L) {
            return RagLtrFeatureExtractor.builtinWeights();
        }
        CachedWeights cached = cache.get(fileId);
        if (cached != null) {
            return cached.weights();
        }
        double[] loaded = loadWeightsFromFileObject(tenantId, fileId);
        if (loaded == null) {
            return RagLtrFeatureExtractor.builtinWeights();
        }
        cache.put(fileId, new CachedWeights(loaded));
        return loaded;
    }

    public void evictCache(long fileObjectId) {
        cache.remove(fileObjectId);
    }

    private double[] loadWeightsFromFileObject(long tenantId, long fileObjectId) {
        // file_object_meta 当前仅 insert/page；按 id+tenant 查 mapper
        FileObjectMeta meta = findMeta(tenantId, fileObjectId);
        if (meta == null) {
            return null;
        }
        MinioClient client = minioClient.getIfAvailable();
        if (client == null) {
            log.warn("[RAG LTR] MinioClient 不可用，回退 builtin：fileObjectId={}", fileObjectId);
            return null;
        }
        try (var stream =
                client.getObject(
                        GetObjectArgs.builder()
                                .bucket(meta.getBucket())
                                .object(meta.getObjectKey())
                                .build())) {
            byte[] bytes = stream.readAllBytes();
            return parseWeightsJson(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn(
                    "[RAG LTR] 加载模型失败 fileObjectId={} tenantId={}：{}",
                    fileObjectId,
                    tenantId,
                    e.toString());
            return null;
        }
    }

    private FileObjectMeta findMeta(long tenantId, long fileObjectId) {
        return fileObjectMetaRepository.findByIdAndTenant(fileObjectId, tenantId).orElse(null);
    }

    private double[] parseWeightsJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.get("weights");
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                return null;
            }
            double[] w = new double[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                w[i] = arr.get(i).asDouble();
            }
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    private record CachedWeights(double[] weights) {}
}
