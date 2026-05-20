package com.aaron.cloud.job;

import com.aaron.cloud.common.api.enums.JobTaskStatus;
import com.aaron.cloud.common.api.enums.JobTaskType;
import com.aaron.cloud.common.jobmeta.JobTaskRepository;
import com.aaron.cloud.common.jobmeta.entity.JobTask;
import com.aaron.cloud.rag.RagIngestOrchestrationService;
import com.aaron.cloud.rag.RagLocalSiteCrawlOrchestrationService;
import com.aaron.cloud.common.task.LongRunningTaskProgress;
import com.aaron.cloud.common.task.LongRunningTaskProgressReporter;
import com.aaron.cloud.common.task.LongRunningTaskProgressSupport;
import com.aaron.cloud.rag.RagVectorInfrastructure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobTaskExecutionService {

    private final JobTaskRepository jobTaskRepository;
    private final ObjectMapper objectMapper;
    private final RagIngestOrchestrationService ragIngestOrchestrationService;
    private final RagLocalSiteCrawlOrchestrationService ragLocalSiteCrawlOrchestrationService;
    private final RagVectorInfrastructure ragVectorInfrastructure;

    public void processTask(long jobTaskId, long tenantId) {
        var taskOpt = jobTaskRepository.findById(jobTaskId, tenantId);
        if (taskOpt.isEmpty()) {
            return;
        }
        JobTask task = taskOpt.get();
        if (task.getTaskType() == JobTaskType.RAG_INDEX
                || task.getTaskType() == JobTaskType.RAG_URL_IMPORT
                || task.getTaskType() == JobTaskType.RAG_FILE_IMPORT
                || task.getTaskType() == JobTaskType.RAG_SITE_CRAWL) {
            if (!ragVectorInfrastructure.isMilvusVectorStore()) {
                jobTaskRepository.updateStatus(
                        jobTaskId,
                        tenantId,
                        JobTaskStatus.FAILED,
                        "{\"error\":\"向量库未启用，任务已终止\"}");
                return;
            }
        }
        jobTaskRepository.updateStatus(jobTaskId, tenantId, JobTaskStatus.RUNNING, null);
        LongRunningTaskProgressReporter progress = progressReporter(jobTaskId, tenantId);
        try {
            String resultJson =
                    switch (task.getTaskType()) {
                        case RAG_INDEX -> {
                            progress.report("INDEX", "索引任务执行中", null, null, null);
                            handleRagIndex(task);
                            yield "{\"indexed\":true}";
                        }
                        case RAG_URL_IMPORT -> handleRagUrlImport(task);
                        case RAG_FILE_IMPORT -> handleRagFileImport(task);
                        case RAG_SITE_CRAWL -> handleRagSiteCrawl(task, progress);
                    };
            jobTaskRepository.updateStatus(jobTaskId, tenantId, JobTaskStatus.SUCCEEDED, resultJson);
        } catch (Exception e) {
            log.error(
                    "job task failed jobTaskId={} tenantId={} taskType={}",
                    jobTaskId,
                    tenantId,
                    task.getTaskType(),
                    e);
            jobTaskRepository.updateStatus(
                    jobTaskId,
                    tenantId,
                    JobTaskStatus.FAILED,
                    "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleRagIndex(JobTask task) throws Exception {
        JsonNode root = objectMapper.readTree(task.getPayloadJson() == null ? "{}" : task.getPayloadJson());
        if (!root.has("kbId")) {
            throw new IllegalArgumentException("kbId required");
        }
        // 占位：真实流水线在后续迭代中接 Milvus / 解析
        root.get("kbId").asLong();
    }

    private String handleRagUrlImport(JobTask task) throws Exception {
        JsonNode root = objectMapper.readTree(task.getPayloadJson() == null ? "{}" : task.getPayloadJson());
        if (!root.has("kbId")) {
            throw new IllegalArgumentException("kbId required");
        }
        if (!root.has("url") || root.get("url").asText("").isBlank()) {
            throw new IllegalArgumentException("url required");
        }
        return ragIngestOrchestrationService.runUrlImport(task.getTenantId(), root);
    }

    private String handleRagSiteCrawl(JobTask task, LongRunningTaskProgressReporter progress)
            throws Exception {
        return ragLocalSiteCrawlOrchestrationService.runFromJobPayload(
                task.getTenantId(), task.getPayloadJson(), progress);
    }

    private LongRunningTaskProgressReporter progressReporter(long jobTaskId, long tenantId) {
        return (stage, message, percent, current, total) ->
                jobTaskRepository.updateProgress(
                        jobTaskId,
                        tenantId,
                        LongRunningTaskProgressSupport.toJson(
                                objectMapper,
                                new LongRunningTaskProgress(stage, message, percent, current, total)));
    }

    private String handleRagFileImport(JobTask task) throws Exception {
        JsonNode root = objectMapper.readTree(task.getPayloadJson() == null ? "{}" : task.getPayloadJson());
        if (!root.has("kbId")) {
            throw new IllegalArgumentException("kbId required");
        }
        if (!root.has("originalFilename") || root.get("originalFilename").asText("").isBlank()) {
            throw new IllegalArgumentException("originalFilename required");
        }
        long kbId = root.get("kbId").asLong();
        return ragIngestOrchestrationService.runFileImport(task.getTenantId(), kbId, root);
    }

    private static String escape(String m) {
        if (m == null) {
            return "";
        }
        return m.replace("\"", "'");
    }
}
