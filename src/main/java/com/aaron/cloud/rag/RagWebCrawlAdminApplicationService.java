package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagWebCrawlSyncMode;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.rag.RagKbDocumentCategoryRepository;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.RagKbDocumentCategory;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.LocalSiteCrawlRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 知识库内一次性网页爬取（非定时任务配置，定时任务见 {@link com.aaron.cloud.scheduled}）。 */
@Service
@RequiredArgsConstructor
public class RagWebCrawlAdminApplicationService {

    private final RagKbDocumentCategoryRepository categoryRepository;
    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final RagLocalSiteCrawlOrchestrationService crawlOrchestrationService;
    private final RagApplicationService ragApplicationService;

    /** 一次性本地规则一条龙爬取（默认入队 RAG_SITE_CRAWL，可在入库任务查看）。 */
    public boolean submitLocalSiteCrawl(long kbId, LocalSiteCrawlRequest req) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        validateCategory(tenantId, kbId, req.getCategoryId());
        boolean asyncJob = req.getAsyncJob() == null || req.getAsyncJob();
        RagWebCrawlSyncMode mode =
                RagWebCrawlSyncMode.fromCode(
                        req.getSyncMode() != null ? req.getSyncMode() : RagWebCrawlSyncMode.FULL.getCode());
        boolean filterCrawled = req.getFilterCrawled() == null || req.getFilterCrawled();
        if (asyncJob) {
            ragApplicationService.enqueueSiteCrawlJob(
                    kbId,
                    req.getBaseUrl().trim(),
                    mode,
                    req.getMaxDepth(),
                    filterCrawled,
                    req.getChunkStrategy(),
                    req.getCategoryId(),
                    null);
        } else {
            crawlOrchestrationService.submitOneShotAsync(
                    tenantId,
                    kbId,
                    req.getBaseUrl().trim(),
                    mode,
                    req.getMaxDepth(),
                    filterCrawled,
                    req.getChunkStrategy(),
                    req.getCategoryId());
        }
        return true;
    }

    private void validateCategory(long tenantId, long kbId, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        RagKbDocumentCategory cat =
                categoryRepository
                        .findById(tenantId, categoryId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid category"));
        if (!Objects.equals(cat.getKbId(), kbId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid category");
        }
    }

    private void requireKb(long kbId, long tenantId) {
        if (ragKnowledgeBaseRepository.findByIdAndTenant(kbId, tenantId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge base not found");
        }
    }
}
