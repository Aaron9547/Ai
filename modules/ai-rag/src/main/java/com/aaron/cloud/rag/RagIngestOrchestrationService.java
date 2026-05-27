package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.rag.RagChunkRetrievalEnabled;
import com.aaron.cloud.common.api.enums.rag.RagChunkStrategy;
import com.aaron.cloud.common.api.enums.rag.RagDocumentDisplayStatus;
import com.aaron.cloud.common.api.enums.rag.RagDocumentSourceType;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
import com.aaron.cloud.rag.runtime.TenantRagRuntimeResolver;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.rag.LnkRagDocumentChunkRepository;
import com.aaron.cloud.common.rag.LnkRagKbDocumentRepository;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.rag.RagDocumentRepository;
import com.aaron.cloud.common.rag.RagKbDocumentCategoryRepository;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.RagKbDocumentCategory;
import com.aaron.cloud.common.rag.entity.LnkRagDocumentChunk;
import com.aaron.cloud.common.rag.entity.LnkRagKbDocument;
import com.aaron.cloud.common.rag.entity.RagChunk;
import com.aaron.cloud.common.rag.entity.RagDocument;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import com.aaron.cloud.common.task.LongRunningTaskProgressReporter;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.rag.crawl.fetch.HttpFetcher;
import com.aaron.cloud.rag.crawl.fetch.PolitenessGate;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import com.aaron.cloud.rag.crawl.policy.SiteCrawlPolicyResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestOrchestrationService {

    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final RagKbDocumentCategoryRepository ragKbDocumentCategoryRepository;
    private final RagDocumentRepository ragDocumentRepository;
    private final RagChunkRepository ragChunkRepository;
    private final LnkRagKbDocumentRepository lnkRagKbDocumentRepository;
    private final LnkRagDocumentChunkRepository lnkRagDocumentChunkRepository;
    private final VectorStorePort vectorStorePort;
    private final RagVectorInfrastructure ragVectorInfrastructure;
    private final RagKbVectorModelGuard ragKbVectorModelGuard;
    private final RagEmbeddingPort ragEmbeddingPort;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final RagWebPageParseService webPageParseService;
    private final RagWebCrawlExtractConfigSupport extractConfigSupport;
    private final RagDocumentChunkPurgeService ragDocumentChunkPurgeService;
    private final HttpFetcher httpFetcher;
    private final PolitenessGate politenessGate;
    private final SiteCrawlPolicyResolver siteCrawlPolicyResolver;
    private final TenantRagRuntimeResolver tenantRagRuntimeResolver;
    private final ObjectProvider<ElasticsearchRagSearchClient> elasticsearchRagSearchClient;

    /** @param root 任务 payload，须含 {@code kbId}、{@code url}，可选 {@code chunkStrategy} 覆盖知识库默认策略。 */
    public String runUrlImport(long tenantId, JsonNode root) throws Exception {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long kbId = root.get("kbId").asLong();
        String url = root.get("url").asText().trim();
        RagKnowledgeBase kb = requireKb(tenantId, kbId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        ArrayNode steps = objectMapper.createArrayNode();
        addStep(steps, "fetch_url", "running", url);
        EffectiveSiteCrawlPolicy policy = siteCrawlPolicyResolver.resolve(tenantId);
        politenessGate.configure(policy.politeness());
        politenessGate.acquire(url, policy.politeness());
        String html;
        try {
            var fetched =
                    httpFetcher.fetch(
                            url, policy.fetch(), siteReferer(url), null, null);
            html = new String(fetched.body(), fetched.charset());
            addStep(steps, "fetch_url", "ok", "bytes=" + fetched.body().length);
        } finally {
            politenessGate.release();
        }
        RagWebCrawlExtractConfig extractConfig = extractConfigFrom(root);
        RagHtmlToMarkdown.ParsedPage page = webPageParseService.parse(html, url, extractConfig);
        String md = page.markdown();
        if (md.isBlank()) {
            throw new IllegalStateException("empty markdown after crawl");
        }
        addStep(steps, "html_to_md", "ok", "chars=" + md.length());
        RagChunkStrategy strategy = effectiveStrategy(kb, root);
        int fixed = effectiveFixed(kb);
        int slide = effectiveSlide(kb);
        String title =
                page.title() != null && !page.title().isBlank()
                        ? page.title().trim()
                        : RagHtmlToMarkdown.resolveDocumentTitle(page, url);
        final long txTenantId = tenantId;
        final long txKbId = kbId;
        final String txMd = md;
        final String txTitle = title;
        final String txUrl = url;
        final RagChunkStrategy txStrategy = strategy;
        final int txFixed = fixed;
        final int txSlide = slide;
        final Long txCategoryId = categoryIdFrom(root);
        PersistResult pr =
                new TransactionTemplate(transactionManager)
                        .execute(
                                status ->
                                        persistMarkdown(
                                                txTenantId,
                                                txKbId,
                                                txMd,
                                                txTitle,
                                                txUrl,
                                                null,
                                                RagDocumentSourceType.URL_CRAWL,
                                                txStrategy,
                                                txFixed,
                                                txSlide,
                                                txCategoryId));
        addStep(steps, "persist", "ok", "documentId=" + pr.documentId + ",chunks=" + pr.chunkCount);
        addStep(steps, "vector", "ok", "collection=kb_" + kbId);
        ObjectNode out = objectMapper.createObjectNode();
        out.set("steps", steps);
        out.put("documentId", pr.documentId);
        out.put("chunkCount", pr.chunkCount);
        out.put("kbId", kbId);
        return objectMapper.writeValueAsString(out);
    }

    public String runFileImport(long tenantId, long kbId, JsonNode root) throws Exception {
        ragVectorInfrastructure.assertMilvusOrThrow();
        RagKnowledgeBase kb = requireKb(tenantId, kbId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        ArrayNode steps = objectMapper.createArrayNode();
        String filename = root.get("originalFilename").asText("").trim();
        String rawMd =
                root.has("markdownContent") && !root.get("markdownContent").isNull()
                        ? root.get("markdownContent").asText("")
                        : "";
        final String markdown =
                rawMd.isBlank() ? "（待接入对象存储）占位正文。文件名：" + filename : rawMd;
        addStep(steps, "markdown_source", "ok", "chars=" + markdown.length());
        RagChunkStrategy strategy = effectiveStrategy(kb, root);
        int fixed = effectiveFixed(kb);
        int slide = effectiveSlide(kb);
        RagDocumentSourceType st =
                root.has("markdownContent") && !root.get("markdownContent").asText("").isBlank()
                        ? RagDocumentSourceType.FILE_UPLOAD
                        : RagDocumentSourceType.FILE;
        final long txTenantId = tenantId;
        final long txKbId = kbId;
        final String txMarkdown = markdown;
        final String txFilename = filename;
        final RagDocumentSourceType txSt = st;
        final RagChunkStrategy txStrategy = strategy;
        final int txFixed = fixed;
        final int txSlide = slide;
        final Long txCategoryId = categoryIdFrom(root);
        PersistResult pr =
                new TransactionTemplate(transactionManager)
                        .execute(
                                status ->
                                        persistMarkdown(
                                                txTenantId,
                                                txKbId,
                                                txMarkdown,
                                                txFilename,
                                                null,
                                                txFilename,
                                                txSt,
                                                txStrategy,
                                                txFixed,
                                                txSlide,
                                                txCategoryId));
        addStep(steps, "persist", "ok", "documentId=" + pr.documentId + ",chunks=" + pr.chunkCount);
        ObjectNode out = objectMapper.createObjectNode();
        out.set("steps", steps);
        out.put("documentId", pr.documentId);
        out.put("chunkCount", pr.chunkCount);
        out.put("kbId", kbId);
        return objectMapper.writeValueAsString(out);
    }

    /** 管理端同步：指定来源 URL 的网页正文入库。 */
    public PersistResult ingestWebMarkdownSync(
            long tenantId,
            long kbId,
            String sourceUrl,
            String markdown,
            String title,
            Integer chunkStrategyCode,
            Long categoryId) {
        ragVectorInfrastructure.assertMilvusOrThrow();
        RagKnowledgeBase kb = requireKb(tenantId, kbId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        RagChunkStrategy strategy =
                chunkStrategyCode != null
                        ? RagChunkStrategy.fromCode(chunkStrategyCode)
                        : effectiveStrategy(kb, null);
        int fixed = effectiveFixed(kb);
        int slide = effectiveSlide(kb);
        final String txUrl = sourceUrl != null ? sourceUrl.trim() : "";
        final String txTitle =
                title != null && !title.isBlank()
                        ? clampDocTitle(title.trim())
                        : clampDocTitle(RagHtmlToMarkdown.resolveDocumentTitle(markdown, txUrl));
        return new TransactionTemplate(transactionManager)
                .execute(
                        status ->
                                persistMarkdown(
                                        tenantId,
                                        kbId,
                                        markdown,
                                        txTitle,
                                        txUrl.isEmpty() ? null : txUrl,
                                        null,
                                        RagDocumentSourceType.URL_CRAWL,
                                        strategy,
                                        fixed,
                                        slide,
                                        categoryId));
    }

    /** 管理端同步上传：与异步任务一致的分片与向量占位写入。 */
    public PersistResult ingestUploadedMarkdownSync(
            long tenantId,
            long kbId,
            String markdown,
            String originalFilename,
            Integer chunkStrategyCode,
            Long categoryId) {
        ragVectorInfrastructure.assertMilvusOrThrow();
        RagKnowledgeBase kb = requireKb(tenantId, kbId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        RagChunkStrategy strategy =
                chunkStrategyCode != null
                        ? RagChunkStrategy.fromCode(chunkStrategyCode)
                        : effectiveStrategy(kb, null);
        int fixed = effectiveFixed(kb);
        int slide = effectiveSlide(kb);
        String name = originalFilename != null ? originalFilename.trim() : "upload.txt";
        final long txTenantId = tenantId;
        final long txKbId = kbId;
        final String txMarkdown = markdown;
        final String txName = name;
        final RagChunkStrategy txStrategy = strategy;
        final int txFixed = fixed;
        final int txSlide = slide;
        final Long txCategoryId = categoryId;
        return new TransactionTemplate(transactionManager)
                .execute(
                        status ->
                                persistMarkdown(
                                        txTenantId,
                                        txKbId,
                                        txMarkdown,
                                        txName,
                                        null,
                                        txName,
                                        RagDocumentSourceType.FILE_UPLOAD,
                                        txStrategy,
                                        txFixed,
                                        txSlide,
                                        txCategoryId));
    }

    public record PersistResult(long documentId, int chunkCount) {}

    private RagKnowledgeBase requireKb(long tenantId, long kbId) {
        RagKnowledgeBase kb = ragKnowledgeBaseRepository.findByIdAndTenant(kbId, tenantId);
        if (kb == null) {
            throw new IllegalArgumentException("kb not found");
        }
        return kb;
    }

    private static RagChunkStrategy effectiveStrategy(RagKnowledgeBase kb, JsonNode payload) {
        if (payload != null && payload.has("chunkStrategy")) {
            return RagChunkStrategy.fromCode(payload.get("chunkStrategy").asInt());
        }
        return kb.getDefaultChunkStrategy() != null ? kb.getDefaultChunkStrategy() : RagChunkStrategy.FIXED_CHAR;
    }

    private RagWebCrawlExtractConfig extractConfigFrom(JsonNode root) {
        if (root == null || !root.has("extractConfig") || root.get("extractConfig").isNull()) {
            return RagWebCrawlExtractConfig.empty();
        }
        try {
            return extractConfigSupport.fromJson(objectMapper.writeValueAsString(root.get("extractConfig")));
        } catch (Exception e) {
            return RagWebCrawlExtractConfig.empty();
        }
    }

    private static int effectiveFixed(RagKnowledgeBase kb) {
        return kb.getChunkFixedChars() != null && kb.getChunkFixedChars() > 0 ? kb.getChunkFixedChars() : 1000;
    }

    private static int effectiveSlide(RagKnowledgeBase kb) {
        return kb.getChunkSlideOverlap() != null && kb.getChunkSlideOverlap() >= 0 ? kb.getChunkSlideOverlap() : 120;
    }

    private static String clampDocTitle(String title) {
        if (title == null) {
            return "";
        }
        String t = title.trim();
        return t.length() > 512 ? t.substring(0, 512) : t;
    }

    private static void addStep(ArrayNode steps, String phase, String status, String detail) {
        ObjectNode n = steps.objectNode();
        n.put("phase", phase);
        n.put("status", status);
        n.put("detail", detail == null ? "" : detail);
        n.put("at", BeijingTime.nowLocal().toString());
        steps.add(n);
    }

    private static Long categoryIdFrom(JsonNode root) {
        if (root == null || !root.has("categoryId") || root.get("categoryId").isNull()) {
            return null;
        }
        return root.get("categoryId").asLong();
    }

    private void applyCategoryIfPresent(RagDocument doc, long tenantId, long kbId, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        RagKbDocumentCategory cat =
                ragKbDocumentCategoryRepository
                        .findById(tenantId, categoryId)
                        .orElseThrow(() -> new IllegalArgumentException("invalid category"));
        if (!Objects.equals(cat.getKbId(), kbId)) {
            throw new IllegalArgumentException("invalid category");
        }
        doc.setCategoryId(categoryId);
    }

    private PersistResult persistMarkdown(
            long tenantId,
            long kbId,
            String md,
            String title,
            String sourceUri,
            String originalFilename,
            RagDocumentSourceType sourceType,
            RagChunkStrategy strategy,
            int fixedChars,
            int slideOverlap,
            Long categoryId) {
        PersistBodyResult body =
                runInRequiresNewTransaction(
                        () ->
                                persistDocumentBody(
                                        tenantId,
                                        kbId,
                                        md,
                                        title,
                                        sourceUri,
                                        originalFilename,
                                        sourceType,
                                        categoryId));
        return indexAfterBodyPersisted(
                tenantId,
                kbId,
                body.documentId(),
                body.documentTitle(),
                md,
                strategy,
                fixedChars,
                slideOverlap);
    }

    private record PersistBodyResult(long documentId, String documentTitle) {}

    /** 正文落库并提交（{@link RagDocumentDisplayStatus#PARSING}），与后续向量化事务分离。 */
    private PersistBodyResult persistDocumentBody(
            long tenantId,
            long kbId,
            String md,
            String title,
            String sourceUri,
            String originalFilename,
            RagDocumentSourceType sourceType,
            Long categoryId) {
        RagDocument doc;
        if (sourceType == RagDocumentSourceType.URL_CRAWL
                && sourceUri != null
                && !sourceUri.isBlank()) {
            var existing =
                    ragDocumentRepository.findLatestActiveByKbAndSourceUri(
                            tenantId, kbId, sourceUri.trim());
            if (existing.isPresent()) {
                doc = existing.get();
                ragDocumentChunkPurgeService.purgeAllChunksForDocument(tenantId, kbId, doc.getId());
                doc.setTitle(title != null ? title : doc.getTitle());
                doc.setSourceUri(sourceUri.trim());
                doc.setMdContent(md);
                doc.setContentLength((long) md.length());
                doc.setDisplayStatus(RagDocumentDisplayStatus.PARSING);
                applyUploadedByFromContext(doc);
                ragDocumentRepository.updateById(doc);
                return new PersistBodyResult(doc.getId(), doc.getTitle());
            }
        }
        doc = new RagDocument();
        doc.setTenantId(tenantId);
        doc.setDeleted(0);
        applyCategoryIfPresent(doc, tenantId, kbId, categoryId);
        doc.setSourceType(sourceType);
        doc.setTitle(title != null ? title : "");
        doc.setSourceUri(sourceUri);
        doc.setOriginalFilename(originalFilename);
        doc.setMdContent(md);
        doc.setContentLength((long) md.length());
        doc.setDisplayStatus(RagDocumentDisplayStatus.PARSING);
        applyUploadedByFromContext(doc);
        ragDocumentRepository.insert(doc);
        LnkRagKbDocument lnk = new LnkRagKbDocument();
        lnk.setKbId(kbId);
        lnk.setDocumentId(doc.getId());
        lnkRagKbDocumentRepository.insert(lnk);
        return new PersistBodyResult(doc.getId(), doc.getTitle());
    }

    /**
     * 正文已在库（可能处于外层事务）；向量化在独立事务中执行。失败时另起事务标 {@link
     * RagDocumentDisplayStatus#INDEX_FAILED} 并清理分片，便于管理端删除。
     */
    private PersistResult indexAfterBodyPersisted(
            long tenantId,
            long kbId,
            long documentId,
            String documentTitle,
            String md,
            RagChunkStrategy strategy,
            int fixedChars,
            int slideOverlap) {
        try {
            return runInRequiresNewTransaction(
                    () ->
                            indexChunksThenPublish(
                                    tenantId,
                                    kbId,
                                    documentId,
                                    documentTitle,
                                    md,
                                    strategy,
                                    fixedChars,
                                    slideOverlap));
        } catch (Exception ex) {
            runInRequiresNewTransaction(
                    () -> markDocumentIndexFailed(tenantId, kbId, documentId));
            throw ex;
        }
    }

    /** 分片 → 嵌入 → Milvus（及混合模式 ES），成功才 {@link RagDocumentDisplayStatus#PUBLISHED}。 */
    private PersistResult indexChunksThenPublish(
            long tenantId,
            long kbId,
            long documentId,
            String documentTitle,
            String md,
            RagChunkStrategy strategy,
            int fixedChars,
            int slideOverlap) {
        RagDocument doc = ragDocumentRepository.findByIdAndTenant(documentId, tenantId);
        if (doc == null || Objects.equals(doc.getDeleted(), 1)) {
            throw new IllegalStateException("document missing: " + documentId);
        }
        updateDocumentDisplayStatus(doc, RagDocumentDisplayStatus.EMBEDDING);
        PersistResult result =
                writeChunksForDocument(
                        tenantId, kbId, documentId, documentTitle, md, strategy, fixedChars, slideOverlap);
        updateDocumentDisplayStatus(doc, RagDocumentDisplayStatus.PUBLISHED);
        return result;
    }

    private void markDocumentIndexFailed(long tenantId, long kbId, long documentId) {
        RagDocument doc = ragDocumentRepository.findByIdAndTenant(documentId, tenantId);
        if (doc == null || Objects.equals(doc.getDeleted(), 1)) {
            return;
        }
        ragDocumentChunkPurgeService.purgeAllChunksForDocument(tenantId, kbId, documentId);
        updateDocumentDisplayStatus(doc, RagDocumentDisplayStatus.INDEX_FAILED);
        log.warn(
                "[RAG 入库] 向量化/索引失败，已标记 INDEX_FAILED 供管理端清理：tenantId={} kbId={} docId={}",
                tenantId,
                kbId,
                documentId);
    }

    private void updateDocumentDisplayStatus(RagDocument doc, RagDocumentDisplayStatus status) {
        doc.setDisplayStatus(status);
        ragDocumentRepository.updateById(doc);
    }

    /** 异步爬取/入库任务执行前须由 {@link com.aaron.cloud.job.JobTaskExecutionService} 绑定租户上下文。 */
    private static void applyUploadedByFromContext(RagDocument doc) {
        var snap = TenantContextHolder.getOrNull();
        if (snap != null && snap.getUserId() != null) {
            doc.setUploadedByUserId(snap.getUserId());
        }
    }

    private <T> T runInRequiresNewTransaction(java.util.function.Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    private void runInRequiresNewTransaction(Runnable action) {
        runInRequiresNewTransaction(
                () -> {
                    action.run();
                    return null;
                });
    }

    private PersistResult writeChunksForDocument(
            long tenantId,
            long kbId,
            long documentId,
            String documentTitle,
            String md,
            RagChunkStrategy strategy,
            int fixedChars,
            int slideOverlap) {
        if (strategy == RagChunkStrategy.PARENT_CHILD) {
            return persistParentChildMarkdown(tenantId, kbId, documentId, documentTitle, md, fixedChars);
        }
        List<String> parts = RagChunkSplitter.split(md, strategy, fixedChars, slideOverlap);
        List<String> chunkIds = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        List<ElasticsearchRagSearchClient.ChunkIndexRow> esRows = new ArrayList<>();
        int seq = 0;
        for (String part : parts) {
            RagChunk ch = new RagChunk();
            ch.setTenantId(tenantId);
            ch.setDeleted(0);
            ch.setContent(part);
            ch.setRetrievalEnabled(RagChunkRetrievalEnabled.ENABLED);
            ch.setParentChunkId(null);
            ragChunkRepository.insert(ch);
            LnkRagDocumentChunk lnkDc = new LnkRagDocumentChunk();
            lnkDc.setDocumentId(documentId);
            lnkDc.setChunkId(ch.getId());
            lnkDc.setSeq(seq++);
            lnkRagDocumentChunkRepository.insert(lnkDc);
            String ref = String.valueOf(ch.getId());
            ch.setEmbeddingRef(ref);
            ragChunkRepository.updateById(ch);
            chunkIds.add(ref);
            vectors.add(ragEmbeddingPort.embed(tenantId, kbId, part));
            esRows.add(new ElasticsearchRagSearchClient.ChunkIndexRow(ch.getId(), part));
        }
        upsertVectorsAndSearchIndex(tenantId, kbId, documentId, documentTitle, chunkIds, vectors, esRows);
        return new PersistResult(documentId, parts.size());
    }

    private PersistResult persistParentChildMarkdown(
            long tenantId,
            long kbId,
            long documentId,
            String documentTitle,
            String md,
            int parentMaxChars) {
        List<RagParentChildChunkSupport.ParentChildBlock> blocks =
                RagParentChildChunkSupport.split(md, parentMaxChars, RagParentChildChunkSupport.DEFAULT_CHILD_CHARS);
        List<String> chunkIds = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        List<ElasticsearchRagSearchClient.ChunkIndexRow> esRows = new ArrayList<>();
        int seq = 0;
        int retrievableCount = 0;
        for (RagParentChildChunkSupport.ParentChildBlock block : blocks) {
            RagChunk parent = new RagChunk();
            parent.setTenantId(tenantId);
            parent.setDeleted(0);
            parent.setContent(block.parentText());
            parent.setRetrievalEnabled(RagChunkRetrievalEnabled.DISABLED);
            parent.setParentChunkId(null);
            ragChunkRepository.insert(parent);
            LnkRagDocumentChunk lnkParent = new LnkRagDocumentChunk();
            lnkParent.setDocumentId(documentId);
            lnkParent.setChunkId(parent.getId());
            lnkParent.setSeq(seq++);
            lnkRagDocumentChunkRepository.insert(lnkParent);
            parent.setEmbeddingRef(null);
            ragChunkRepository.updateById(parent);

            for (String childText : block.childTexts()) {
                RagChunk child = new RagChunk();
                child.setTenantId(tenantId);
                child.setDeleted(0);
                child.setContent(childText);
                child.setRetrievalEnabled(RagChunkRetrievalEnabled.ENABLED);
                child.setParentChunkId(parent.getId());
                ragChunkRepository.insert(child);
                LnkRagDocumentChunk lnkChild = new LnkRagDocumentChunk();
                lnkChild.setDocumentId(documentId);
                lnkChild.setChunkId(child.getId());
                lnkChild.setSeq(seq++);
                lnkRagDocumentChunkRepository.insert(lnkChild);
                String ref = String.valueOf(child.getId());
                child.setEmbeddingRef(ref);
                ragChunkRepository.updateById(child);
                chunkIds.add(ref);
                vectors.add(ragEmbeddingPort.embed(tenantId, kbId, childText));
                esRows.add(new ElasticsearchRagSearchClient.ChunkIndexRow(child.getId(), childText));
                retrievableCount++;
            }
        }
        upsertVectorsAndSearchIndex(tenantId, kbId, documentId, documentTitle, chunkIds, vectors, esRows);
        return new PersistResult(documentId, retrievableCount);
    }

    private void upsertVectorsAndSearchIndex(
            long tenantId,
            long kbId,
            long documentId,
            String documentTitle,
            List<String> chunkIds,
            List<float[]> vectors,
            List<ElasticsearchRagSearchClient.ChunkIndexRow> esRows) {
        if (!chunkIds.isEmpty()) {
            vectorStorePort.upsertChunks(tenantId, "kb_" + kbId, chunkIds, vectors);
            indexElasticsearchIfHybrid(tenantId, kbId, documentId, documentTitle, esRows);
        }
    }

    private void indexElasticsearchIfHybrid(
            long tenantId,
            long kbId,
            long documentId,
            String documentTitle,
            List<ElasticsearchRagSearchClient.ChunkIndexRow> esRows) {
        if (tenantRagRuntimeResolver.resolveRetrievalMode(tenantId) != RagRetrievalMode.MILVUS_ES_HYBRID
                || esRows == null
                || esRows.isEmpty()) {
            return;
        }
        ElasticsearchRagSearchClient es = elasticsearchRagSearchClient.getIfAvailable();
        if (es == null) {
            log.warn(
                    "[RAG 入库] 检索模式为 Milvus+ES 混合，但 Elasticsearch 客户端未装配，跳过 ES 索引：kbId={} docId={}",
                    kbId,
                    documentId);
            return;
        }
        es.indexChunks(tenantId, kbId, documentId, documentTitle, esRows);
    }

    private static String siteReferer(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            if (u.getHost() == null) {
                return null;
            }
            return u.getScheme() + "://" + u.getHost() + "/";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 全库向量重建：遍历知识库内未删除文档，对已有 Markdown 正文的条目重新分片并写入 Milvus（混合模式时同步
     * ES）。由 {@link com.aaron.cloud.job.JobTaskExecutionService} 的 {@code RAG_INDEX} 任务调用。
     */
    public String runKbReindex(long tenantId, long kbId, LongRunningTaskProgressReporter progress)
            throws Exception {
        ragVectorInfrastructure.assertMilvusOrThrow();
        RagKnowledgeBase kb = requireKb(tenantId, kbId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        List<RagDocument> docs = ragDocumentRepository.listActiveByKbId(tenantId, kbId);
        RagChunkStrategy strategy = effectiveStrategy(kb, null);
        int fixed = effectiveFixed(kb);
        int slide = effectiveSlide(kb);
        int total = docs.size();
        int indexed = 0;
        int skipped = 0;
        int failed = 0;
        int chunkCount = 0;
        progress.report("REINDEX", "开始全库索引，共 " + total + " 篇文档", 0, 0, total);
        for (int i = 0; i < docs.size(); i++) {
            RagDocument doc = docs.get(i);
            int pct = total > 0 ? (i * 100 / total) : 100;
            String title =
                    doc.getTitle() != null && !doc.getTitle().isBlank()
                            ? doc.getTitle().trim()
                            : ("#" + doc.getId());
            progress.report("REINDEX", "正在处理：" + title, pct, i + 1, total);
            String md = doc.getMdContent();
            if (md == null || md.isBlank()) {
                skipped++;
                continue;
            }
            try {
                ragDocumentChunkPurgeService.purgeAllChunksForDocument(tenantId, kbId, doc.getId());
                PersistResult pr =
                        indexAfterBodyPersisted(
                                tenantId, kbId, doc.getId(), doc.getTitle(), md, strategy, fixed, slide);
                indexed++;
                chunkCount += pr.chunkCount();
            } catch (Exception ex) {
                failed++;
                log.warn(
                        "[RAG 全库索引] 文档失败 tenantId={} kbId={} docId={} title={}",
                        tenantId,
                        kbId,
                        doc.getId(),
                        title,
                        ex);
            }
        }
        String summary =
                String.format(
                        "全库索引完成：%d 篇成功，%d 篇跳过（无正文），%d 篇失败，共 %d 个分片",
                        indexed, skipped, failed, chunkCount);
        log.info(
                "[RAG 全库索引] tenantId={} kbId={} total={} indexed={} skipped={} failed={} chunks={}",
                tenantId,
                kbId,
                total,
                indexed,
                skipped,
                failed,
                chunkCount);
        progress.report("DONE", summary, 100, indexed, total);
        ObjectNode out = objectMapper.createObjectNode();
        out.put("kbId", kbId);
        out.put("documentTotal", total);
        out.put("indexedDocuments", indexed);
        out.put("skippedDocuments", skipped);
        out.put("failedDocuments", failed);
        out.put("chunkCount", chunkCount);
        out.put("summary", summary);
        return objectMapper.writeValueAsString(out);
    }
}
