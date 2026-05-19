package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagChunkRetrievalEnabled;
import com.aaron.cloud.common.api.enums.RagChunkStrategy;
import com.aaron.cloud.common.api.enums.RagDocumentDisplayStatus;
import com.aaron.cloud.common.api.enums.RagDocumentSourceType;
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
import com.aaron.cloud.common.time.BeijingTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
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

    /** @param root 浠诲姟 payload锛岄』鍚?{@code kbId}銆亄@code url}锛屽彲閫?{@code chunkStrategy} 鏁存暟鐮佽鐩栫煡璇嗗簱榛樿绛栫暐銆?*/
    public String runUrlImport(long tenantId, JsonNode root) throws Exception {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long kbId = root.get("kbId").asLong();
        String url = root.get("url").asText().trim();
        RagKnowledgeBase kb = requireKb(tenantId, kbId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        ArrayNode steps = objectMapper.createArrayNode();
        addStep(steps, "fetch_url", "running", url);
        RagHttpFetch.Fetched fetched = RagHttpFetch.get(url);
        String html = new String(fetched.body(), fetched.charset());
        addStep(steps, "fetch_url", "ok", "bytes=" + fetched.body().length);
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
        String title = RagHtmlToMarkdown.resolveDocumentTitle(page, url);
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

    /** 绠＄悊绔悓姝ヤ笂浼狅細涓庡紓姝ヤ换鍔′竴鑷寸殑鍒嗙墖涓庡悜閲忓崰浣嶅啓鍏ャ€?*/
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
                doc.setDisplayStatus(RagDocumentDisplayStatus.PUBLISHED);
                ragDocumentRepository.updateById(doc);
                return writeChunksForDocument(
                        tenantId, kbId, doc.getId(), md, strategy, fixedChars, slideOverlap);
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
        doc.setDisplayStatus(RagDocumentDisplayStatus.PUBLISHED);
        var snap = TenantContextHolder.getOrNull();
        if (snap != null && snap.getUserId() != null) {
            doc.setUploadedByUserId(snap.getUserId());
        }
        ragDocumentRepository.insert(doc);
        LnkRagKbDocument lnk = new LnkRagKbDocument();
        lnk.setKbId(kbId);
        lnk.setDocumentId(doc.getId());
        lnkRagKbDocumentRepository.insert(lnk);
        return writeChunksForDocument(
                tenantId, kbId, doc.getId(), md, strategy, fixedChars, slideOverlap);
    }

    private PersistResult writeChunksForDocument(
            long tenantId,
            long kbId,
            long documentId,
            String md,
            RagChunkStrategy strategy,
            int fixedChars,
            int slideOverlap) {
        if (strategy == RagChunkStrategy.PARENT_CHILD) {
            return persistParentChildMarkdown(tenantId, kbId, documentId, md, fixedChars);
        }
        List<String> parts = RagChunkSplitter.split(md, strategy, fixedChars, slideOverlap);
        List<String> chunkIds = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
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
        }
        if (!chunkIds.isEmpty()) {
            vectorStorePort.upsertChunks(tenantId, "kb_" + kbId, chunkIds, vectors);
        }
        return new PersistResult(documentId, parts.size());
    }

    private PersistResult persistParentChildMarkdown(
            long tenantId, long kbId, long documentId, String md, int parentMaxChars) {
        List<RagParentChildChunkSupport.ParentChildBlock> blocks =
                RagParentChildChunkSupport.split(md, parentMaxChars, RagParentChildChunkSupport.DEFAULT_CHILD_CHARS);
        List<String> chunkIds = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
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
                retrievableCount++;
            }
        }
        if (!chunkIds.isEmpty()) {
            vectorStorePort.upsertChunks(tenantId, "kb_" + kbId, chunkIds, vectors);
        }
        return new PersistResult(documentId, retrievableCount);
    }
}
