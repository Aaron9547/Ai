package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.JobTaskStatus;
import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.api.enums.JobTaskType;
import com.aaron.cloud.common.api.enums.RagChunkStrategy;
import com.aaron.cloud.common.api.enums.RagWebCrawlSyncMode;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.jobmeta.JobTaskRepository;
import com.aaron.cloud.common.jobmeta.entity.JobTask;
import com.aaron.cloud.common.rag.RagKbDocumentCategoryRepository;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.RagKbDocumentCategory;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import com.aaron.cloud.job.JobDispatchMessage;
import com.aaron.cloud.job.JobPublisher;
import com.aaron.cloud.job.JobTaskExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagApplicationService {

    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final RagKbDocumentCategoryRepository ragKbDocumentCategoryRepository;
    private final JobTaskRepository jobTaskRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<JobPublisher> jobPublisher;
    private final JobTaskExecutionService jobTaskExecutionService;
    private final RagKbVectorModelGuard ragKbVectorModelGuard;

    public RagKnowledgeBase createKb(String name) {
        var snap = TenantContextHolder.require();
        var kb = new RagKnowledgeBase();
        kb.setTenantId(snap.getTenantId());
        kb.setName(name);
        kb.setDefaultChunkStrategy(RagChunkStrategy.SEMANTIC);
        kb.setChunkFixedChars(1000);
        kb.setChunkSlideOverlap(120);
        kb.setChatVectorMinCosineScore(0.65d);
        // 新建知识库默认不参与对话检索；开启前须在高级设置绑定向量模型（与 PROJECT.md RAG 约定一致）。
        kb.setChatRetrievalEnabled(ToggleState.OFF);
        ragKnowledgeBaseRepository.insert(kb);
        seedDefaultDocCategories(snap.getTenantId(), kb.getId());
        return kb;
    }

    /** 存量知识库无分类时由管理端列表懒创建默认「试题」「评测报告」。 */
    public void ensureDefaultDocCategories(long tenantId, long kbId) {
        if (ragKbDocumentCategoryRepository.listByKb(tenantId, kbId).isEmpty()) {
            seedDefaultDocCategories(tenantId, kbId);
        }
    }

    private void seedDefaultDocCategories(long tenantId, long kbId) {
        int order = 10;
        for (String name : new String[] {"试题", "评测报告"}) {
            var c = new RagKbDocumentCategory();
            c.setTenantId(tenantId);
            c.setKbId(kbId);
            c.setName(name);
            c.setSortOrder(order);
            order += 10;
            ragKbDocumentCategoryRepository.insert(c);
        }
    }

    public long enqueueIndexJob(long kbId) throws Exception {
        var snap = TenantContextHolder.require();
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", kbId);
        var task = new JobTask();
        task.setTenantId(snap.getTenantId());
        task.setUserId(snap.getUserId());
        task.setDeviceId(snap.getDeviceId());
        task.setTaskType(JobTaskType.RAG_INDEX);
        task.setStatus(JobTaskStatus.PENDING);
        task.setPayloadJson(objectMapper.writeValueAsString(payload));
        task.setRagKbId(kbId);
        jobTaskRepository.insert(task);
        dispatchJobTask(task);
        return task.getId();
    }

    /** 网页入库锛氬悓鍩熺埇鍙栥€丮arkdown銆佸垎鍧椾笌鍚戦噺鍖栫敱 job 娴佹按绾挎墿灞曪紱姝ゅ浠呭叆闃熴€?*/
    public long enqueueUrlImportJob(long kbId, String url, Integer chunkStrategy, Long categoryId)
            throws Exception {
        var snap = TenantContextHolder.require();
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(snap.getTenantId(), kbId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", kbId);
        payload.put("url", url.trim());
        if (chunkStrategy != null) {
            payload.put("chunkStrategy", chunkStrategy);
        }
        if (categoryId != null) {
            payload.put("categoryId", categoryId);
        }
        var task = new JobTask();
        task.setTenantId(snap.getTenantId());
        task.setUserId(snap.getUserId());
        task.setDeviceId(snap.getDeviceId());
        task.setTaskType(JobTaskType.RAG_URL_IMPORT);
        task.setStatus(JobTaskStatus.PENDING);
        task.setPayloadJson(objectMapper.writeValueAsString(payload));
        task.setRagKbId(kbId);
        jobTaskRepository.insert(task);
        dispatchJobTask(task);
        return task.getId();
    }

    /** 文件入知识库锛氫笌 {@code file} 棰勭鍚嶄笂浼犺鎺ュ墠锛屽厛鍏ラ槦鍗犱綅浠诲姟銆?*/
    public long enqueueFileImportJob(
            long kbId,
            String originalFilename,
            String contentType,
            String markdownContent,
            Integer chunkStrategy,
            Long categoryId)
            throws Exception {
        var snap = TenantContextHolder.require();
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(snap.getTenantId(), kbId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", kbId);
        payload.put("originalFilename", originalFilename == null ? "" : originalFilename.trim());
        payload.put("contentType", contentType == null ? "" : contentType.trim());
        if (markdownContent != null && !markdownContent.isBlank()) {
            payload.put("markdownContent", markdownContent);
        }
        if (chunkStrategy != null) {
            payload.put("chunkStrategy", chunkStrategy);
        }
        if (categoryId != null) {
            payload.put("categoryId", categoryId);
        }
        var task = new JobTask();
        task.setTenantId(snap.getTenantId());
        task.setUserId(snap.getUserId());
        task.setDeviceId(snap.getDeviceId());
        task.setTaskType(JobTaskType.RAG_FILE_IMPORT);
        task.setStatus(JobTaskStatus.PENDING);
        task.setPayloadJson(objectMapper.writeValueAsString(payload));
        task.setRagKbId(kbId);
        jobTaskRepository.insert(task);
        dispatchJobTask(task);
        return task.getId();
    }

    /** 站点本地规则一条龙爬取任务。 */
    public long enqueueSiteCrawlJob(
            long kbId,
            String baseUrl,
            RagWebCrawlSyncMode syncMode,
            Integer maxDepth,
            boolean filterCrawled,
            Integer chunkStrategy,
            Long categoryId,
            Long scheduleId)
            throws Exception {
        var snap = TenantContextHolder.require();
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(snap.getTenantId(), kbId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", kbId);
        payload.put("baseUrl", baseUrl.trim());
        payload.put("syncMode", syncMode.getCode());
        payload.put("filterCrawled", filterCrawled);
        if (maxDepth != null) {
            payload.put("maxDepth", maxDepth);
        }
        if (chunkStrategy != null) {
            payload.put("chunkStrategy", chunkStrategy);
        }
        if (categoryId != null) {
            payload.put("categoryId", categoryId);
        }
        if (scheduleId != null) {
            payload.put("scheduleId", scheduleId);
        }
        var task = new JobTask();
        task.setTenantId(snap.getTenantId());
        task.setUserId(snap.getUserId());
        task.setDeviceId(snap.getDeviceId());
        task.setTaskType(JobTaskType.RAG_SITE_CRAWL);
        task.setStatus(JobTaskStatus.PENDING);
        task.setPayloadJson(objectMapper.writeValueAsString(payload));
        task.setRagKbId(kbId);
        jobTaskRepository.insert(task);
        dispatchJobTask(task);
        return task.getId();
    }

    /** 定时爬站配置入队（siteId 写入 payload，供 url_item.schedule_id 关联）。 */
    public long enqueueSiteCrawlJobForSite(
            long tenantId,
            long kbId,
            String baseUrl,
            RagWebCrawlSyncMode syncMode,
            Integer maxDepth,
            boolean filterCrawled,
            Integer chunkStrategy,
            Long categoryId,
            long siteId)
            throws Exception {
        return enqueueSiteCrawlJobForSite(
                tenantId,
                kbId,
                baseUrl,
                syncMode,
                maxDepth,
                filterCrawled,
                chunkStrategy,
                categoryId,
                siteId,
                null);
    }

    /**
     * @param triggeredByUserId 手动「立即爬取」时传当前管理员 {@code sec_user_account.id}；定时调度传 null
     */
    public long enqueueSiteCrawlJobForSite(
            long tenantId,
            long kbId,
            String baseUrl,
            RagWebCrawlSyncMode syncMode,
            Integer maxDepth,
            boolean filterCrawled,
            Integer chunkStrategy,
            Long categoryId,
            long siteId,
            Long triggeredByUserId)
            throws Exception {
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", kbId);
        payload.put("baseUrl", baseUrl.trim());
        payload.put("syncMode", syncMode.getCode());
        payload.put("filterCrawled", filterCrawled);
        payload.put("siteId", siteId);
        payload.put("scheduleId", siteId);
        if (maxDepth != null) {
            payload.put("maxDepth", maxDepth);
        }
        if (chunkStrategy != null) {
            payload.put("chunkStrategy", chunkStrategy);
        }
        if (categoryId != null) {
            payload.put("categoryId", categoryId);
        }
        var task = new JobTask();
        task.setTenantId(tenantId);
        task.setUserId(triggeredByUserId);
        task.setTaskType(JobTaskType.RAG_SITE_CRAWL);
        task.setStatus(JobTaskStatus.PENDING);
        task.setPayloadJson(objectMapper.writeValueAsString(payload));
        task.setRagKbId(kbId);
        jobTaskRepository.insert(task);
        dispatchJobTask(task);
        return task.getId();
    }

    /**
     * 任务入队后异步执行：优先 RocketMQ；未启用 MQ 时走后台线程，避免 HTTP 请求同步跑完整站爬取。
     */
    private void dispatchJobTask(JobTask task) {
        dispatchJobTask(task.getId(), task.getTenantId(), task.getDeviceId(), task.getUserId());
    }

    private void dispatchJobTask(long jobTaskId, long tenantId, String deviceId, Long userId) {
        JobPublisher pub = jobPublisher.getIfAvailable();
        if (pub != null) {
            pub.publish(
                    JobDispatchMessage.builder()
                            .traceId(MDC.get("traceId"))
                            .tenantId(tenantId)
                            .deviceId(deviceId)
                            .userId(userId)
                            .jobTaskId(jobTaskId)
                            .build());
            return;
        }
        CompletableFuture.runAsync(() -> jobTaskExecutionService.processTask(jobTaskId, tenantId));
    }
}
