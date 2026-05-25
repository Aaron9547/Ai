package com.aaron.cloud.rag;

import com.aaron.cloud.common.rag.CrawlRunRepository;
import com.aaron.cloud.common.rag.CrawlUrlQueueRepository;
import com.aaron.cloud.common.rag.LnkRagKbDocumentRepository;
import com.aaron.cloud.common.rag.RagDocumentRepository;
import com.aaron.cloud.common.rag.RagWebCrawlUrlItemRepository;
import com.aaron.cloud.common.rag.entity.CrawlRun;
import com.aaron.cloud.common.rag.entity.RagDocument;
import com.aaron.cloud.common.rag.entity.RagWebCrawlUrlItem;
import com.aaron.cloud.common.time.BeijingTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 删除站点爬取配置时，清理关联文档（向量 + 软删）与 crawl_run 队列痕迹。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSiteCrawlPurgeService {

    private final RagWebCrawlUrlItemRepository urlItemRepository;
    private final RagDocumentRepository ragDocumentRepository;
    private final LnkRagKbDocumentRepository lnkRagKbDocumentRepository;
    private final RagDocumentChunkPurgeService ragDocumentChunkPurgeService;
    private final CrawlRunRepository crawlRunRepository;
    private final CrawlUrlQueueRepository crawlUrlQueueRepository;

    /**
     * 软删本站点爬取产生的全部文档：Milvus 向量、分片、KB 关联、url_item 标记。
     *
     * @return 实际软删的文档数
     */
    public int purgeDocumentsForSite(long tenantId, long kbId, long siteId) {
        List<RagWebCrawlUrlItem> items = urlItemRepository.listActiveBySchedule(tenantId, siteId);
        Set<Long> docIds = new HashSet<>();
        for (RagWebCrawlUrlItem item : items) {
            if (item.getDocumentId() != null) {
                docIds.add(item.getDocumentId());
            }
        }
        int purged = 0;
        for (Long docId : docIds) {
            if (softDeleteKbDocument(tenantId, kbId, docId)) {
                purged++;
            }
        }
        urlItemRepository.markDeletedBySchedule(tenantId, siteId);
        log.info(
                "站点爬取文档清理 tenantId={} kbId={} siteId={} docCandidates={} purged={}",
                tenantId,
                kbId,
                siteId,
                docIds.size(),
                purged);
        return purged;
    }

    /** 删除本站点相关的 crawl_run 与 crawl_url_queue（不删 rag_web_crawl_site 行本身）。 */
    public int purgeCrawlRunsForSite(long tenantId, long kbId, long siteId) {
        List<CrawlRun> runs = crawlRunRepository.listByKb(tenantId, kbId, 5000, siteId);
        if (runs.isEmpty()) {
            return 0;
        }
        List<Long> runIds = runs.stream().map(CrawlRun::getId).toList();
        crawlUrlQueueRepository.deleteByRunIds(runIds);
        int n = crawlRunRepository.deleteBySiteId(tenantId, kbId, siteId);
        log.info("站点 crawl_run 清理 tenantId={} kbId={} siteId={} runs={}", tenantId, kbId, siteId, n);
        return n;
    }

    private boolean softDeleteKbDocument(long tenantId, long kbId, long documentId) {
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            return false;
        }
        RagDocument doc = ragDocumentRepository.findByIdAndTenant(documentId, tenantId);
        if (doc == null || doc.getDeleted() != null && doc.getDeleted() == 1) {
            return false;
        }
        ragDocumentChunkPurgeService.purgeAllChunksForDocument(tenantId, kbId, documentId);
        urlItemRepository.markDeletedByDocumentIds(tenantId, List.of(documentId));
        lnkRagKbDocumentRepository.deleteLink(kbId, documentId);
        doc.setDeleted(1);
        doc.setDeletedAt(BeijingTime.nowLocal());
        ragDocumentRepository.updateById(doc);
        return true;
    }
}
