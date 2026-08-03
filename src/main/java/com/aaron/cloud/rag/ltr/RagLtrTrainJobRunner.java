package com.aaron.cloud.rag.ltr;

import com.aaron.cloud.common.api.enums.file.FileScanStatus;
import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.filemeta.FileObjectMetaRepository;
import com.aaron.cloud.common.filemeta.entity.FileObjectMeta;
import com.aaron.cloud.common.jobmeta.JobTaskRepository;
import com.aaron.cloud.common.jobmeta.entity.JobTask;
import com.aaron.cloud.common.observability.ObsRagHitEventRepository;
import com.aaron.cloud.common.observability.entity.ObsRagHitEvent;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningEffectiveService;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService.PutItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** RAG LTR 训练 Job 执行体（仅被 ai-job 调度，不反向依赖 {@code JobTaskExecutionPort}）。 */
@Service
@RequiredArgsConstructor
public class RagLtrTrainJobRunner {

    static final int DEFAULT_DAYS = 30;
    static final int DEFAULT_MAX_SAMPLES = 5000;

    private static final String MODEL_BUCKET = "ai-rag-ltr";

    private final ObsRagHitEventRepository obsRagHitEventRepository;
    private final FileObjectMetaRepository fileObjectMetaRepository;
    private final ObjectProvider<MinioClient> minioClient;
    private final ObjectMapper objectMapper;
    private final RagRetrievalTuningEffectiveService ragRetrievalTuningEffectiveService;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final JobTaskRepository jobTaskRepository;
    private final RagLtrModelStore ragLtrModelStore;

    public String runTrainingJob(long tenantId, long jobTaskId) throws Exception {
        JobTask task = jobTaskRepository.findById(jobTaskId, tenantId).orElseThrow();
        var root = objectMapper.readTree(task.getPayloadJson() == null ? "{}" : task.getPayloadJson());
        int days = root.path("days").asInt(DEFAULT_DAYS);
        int maxSamples = root.path("maxSamples").asInt(DEFAULT_MAX_SAMPLES);
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days));
        var page =
                obsRagHitEventRepository.pageForAdmin(
                        tenantId, 1, maxSamples, null, null, null, null, null, null, since);
        List<ObsRagHitEvent> samples = page.getRecords();
        double[] weights = fitWeights(samples);
        String modelJson = serializeWeights(weights);
        long fileObjectId = persistModel(tenantId, jobTaskId, modelJson);
        activateModelPointer(tenantId, jobTaskId, fileObjectId);
        ragLtrModelStore.evictCache(fileObjectId);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("sampleCount", samples.size());
        result.put("fileObjectId", fileObjectId);
        result.put("smileFormat", "linear-weights-v1");
        result.put("ltrModelVersion", "train-" + jobTaskId);
        ObjectNode metrics = result.putObject("metrics");
        metrics.put("ndcg@10", estimateNdcgProxy(samples));
        return objectMapper.writeValueAsString(result);
    }

    private double[] fitWeights(List<ObsRagHitEvent> samples) {
        double[] w = RagLtrFeatureExtractor.builtinWeights();
        if (samples == null || samples.size() < 20) {
            return w;
        }
        double[] sumPos = new double[w.length];
        double[] sumNeg = new double[w.length];
        int pos = 0;
        int neg = 0;
        for (ObsRagHitEvent e : samples) {
            double label = rankLabel(e.getRankInBatch());
            double[] f = obsFeature(e);
            if (label >= 0.6d) {
                for (int i = 0; i < f.length; i++) {
                    sumPos[i] += f[i];
                }
                pos++;
            } else {
                for (int i = 0; i < f.length; i++) {
                    sumNeg[i] += f[i];
                }
                neg++;
            }
        }
        if (pos == 0 || neg == 0) {
            return w;
        }
        for (int i = 0; i < w.length; i++) {
            double delta = (sumPos[i] / pos) - (sumNeg[i] / neg);
            w[i] = Math.max(0.01d, w[i] + delta * 0.35d);
        }
        normalize(w);
        return w;
    }

    private static double[] obsFeature(ObsRagHitEvent e) {
        double[] f = new double[RagLtrFeatureExtractor.FEATURE_COUNT];
        f[0] = e.getVectorSimilarity() == null ? 0.0d : e.getVectorSimilarity().doubleValue();
        f[1] = e.getKeywordScore() == null ? 0.0d : Math.min(1.0d, e.getKeywordScore().doubleValue() / 10.0d);
        f[2] = e.getRankInBatch() == null ? 0.0d : 1.0d / (e.getRankInBatch() + 10.0d);
        f[3] = 0.0d;
        f[4] = 0.05d;
        f[5] = 0.05d;
        f[6] = e.getHitSource() != null && e.getHitSource().name().equals("MILVUS") ? 1.0d : 0.0d;
        f[7] = e.getQueryText() == null ? 0.0d : Math.min(1.0d, e.getQueryText().length() / 64.0d);
        return f;
    }

    private static double rankLabel(Integer rank) {
        if (rank == null || rank <= 0) {
            return 0.3d;
        }
        if (rank == 1) {
            return 1.0d;
        }
        if (rank <= 3) {
            return 0.75d;
        }
        return 0.4d;
    }

    private static void normalize(double[] w) {
        double s = 0.0d;
        for (double v : w) {
            s += v;
        }
        if (s <= 0.0d) {
            return;
        }
        for (int i = 0; i < w.length; i++) {
            w[i] = w[i] / s;
        }
    }

    private static double estimateNdcgProxy(List<ObsRagHitEvent> samples) {
        if (samples == null || samples.isEmpty()) {
            return 0.0d;
        }
        long top1 = samples.stream().filter(e -> e.getRankInBatch() != null && e.getRankInBatch() == 1).count();
        return Math.min(0.99d, (double) top1 / samples.size() + 0.55d);
    }

    private String serializeWeights(double[] weights) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("format", "linear-weights-v1");
        List<Double> list = new ArrayList<>();
        for (double w : weights) {
            list.add(w);
        }
        body.put("weights", list);
        return objectMapper.writeValueAsString(body);
    }

    private long persistModel(long tenantId, long jobTaskId, String modelJson) throws Exception {
        MinioClient client = minioClient.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("MinioClient 不可用，无法存储 LTR 模型");
        }
        byte[] bytes = modelJson.getBytes(StandardCharsets.UTF_8);
        String objectKey = "tenant-" + tenantId + "/ltr/job-" + jobTaskId + ".json";
        client.putObject(
                PutObjectArgs.builder()
                        .bucket(MODEL_BUCKET)
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType("application/json")
                        .build());
        FileObjectMeta meta = new FileObjectMeta();
        meta.setTenantId(tenantId);
        meta.setBucket(MODEL_BUCKET);
        meta.setObjectKey(objectKey);
        meta.setSizeBytes((long) bytes.length);
        meta.setContentType("application/json");
        meta.setScanStatus(FileScanStatus.UNKNOWN);
        fileObjectMetaRepository.insert(meta);
        return meta.getId();
    }

    private void activateModelPointer(long tenantId, long jobTaskId, long fileObjectId) throws Exception {
        RagRetrievalTuningRuntime current = ragRetrievalTuningEffectiveService.effective(tenantId);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("rewriteEnabled", current.rewriteEnabled());
        node.put("rewriteSemanticMinSimilarity", current.rewriteSemanticMinSimilarity());
        if (current.rewriteModelId() != null) {
            node.put("rewriteModelId", current.rewriteModelId());
        }
        node.put("rewriteContextMaxChars", current.rewriteContextMaxChars());
        node.put("simpleQueryFastPathEnabled", current.simpleQueryFastPathEnabled());
        node.put("simpleQueryMaxChars", current.simpleQueryMaxChars());
        node.put("hybridLtrEnabled", current.hybridLtrEnabled());
        node.put("ltrCandidateMultiplier", current.ltrCandidateMultiplier());
        node.put("activeLtrFileObjectId", fileObjectId);
        node.put("activeLtrJobTaskId", jobTaskId);
        node.put("ltrModelVersion", "train-" + jobTaskId);
        PutItem item = new PutItem();
        item.setKey(TenantRuntimeSettingKey.RAG_RETRIEVAL_TUNING_JSON.getStorage());
        item.setValueText(objectMapper.writeValueAsString(node));
        tenantRuntimeSettingApplicationService.replace(tenantId, List.of(item));
    }
}
