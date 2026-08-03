package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.document.TikaDocumentTextExtractor;
import com.aaron.cloud.rag.runtime.TenantRagRuntimeResolver;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.llm.LlmModelStatus;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.api.enums.rag.RagChunkRetrievalEnabled;
import com.aaron.cloud.common.api.enums.rag.RagChunkStrategy;
import com.aaron.cloud.common.api.enums.rag.RagDocumentDisplayStatus;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.rag.LnkRagDocumentChunkRepository;
import com.aaron.cloud.common.rag.LnkRagKbDocumentRepository;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.rag.RagDocumentRepository;
import com.aaron.cloud.common.rag.RagKbDocumentCategoryRepository;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.RagWebCrawlUrlItemRepository;
import com.aaron.cloud.common.rag.entity.LnkRagDocumentChunk;
import com.aaron.cloud.common.rag.entity.RagChunk;
import com.aaron.cloud.common.rag.entity.RagDocument;
import com.aaron.cloud.common.rag.entity.RagKbDocumentCategory;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.tenant.entity.SysTenant;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.CreateRagDocumentCategoryRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.CreateRagKbRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.FileIngestJobRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.PatchRagDocumentRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.CreateRagChunkRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagChunkAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentChunksListResponse;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagChunkPatchRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagChunkUpdateRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentCategoryAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.IngestAnalyzeView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentUploadResponse;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagKbAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagKbSettingsPatchRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.UpdateRagDocumentCategoryRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.UpdateRagKbRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagRetrievalTestHitView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagRetrievalTestRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagRetrievalTestView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.UrlImportJobRequest;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RagKbAdminApplicationService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int UPLOAD_MAX_BYTES = 8_000_000;

    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final LnkRagKbDocumentRepository lnkRagKbDocumentRepository;
    private final RagApplicationService ragApplicationService;
    private final RagDocumentRepository ragDocumentRepository;
    private final RagChunkRepository ragChunkRepository;
    private final LnkRagDocumentChunkRepository lnkRagDocumentChunkRepository;
    private final RagIngestOrchestrationService ragIngestOrchestrationService;
    private final VectorStorePort vectorStorePort;
    private final RagEmbeddingPort ragEmbeddingPort;
    private final SysLlmModelRepository sysLlmModelRepository;
    private final RagKbDocumentCategoryRepository ragKbDocumentCategoryRepository;
    private final SecUserAccountRepository secUserAccountRepository;
    private final RagVectorInfrastructure ragVectorInfrastructure;
    private final RagKbVectorModelGuard ragKbVectorModelGuard;
    private final RagQueryBridgeService ragQueryBridgeService;
    private final TenantRagRuntimeResolver tenantRagRuntimeResolver;
    private final SysTenantRepository sysTenantRepository;
    private final RagIngestPreviewApplicationService ragIngestPreviewApplicationService;
    private final RagDocumentChunkPurgeService ragDocumentChunkPurgeService;
    private final RagWebCrawlUrlItemRepository ragWebCrawlUrlItemRepository;
    private final TikaDocumentTextExtractor documentTextExtractor;

    /**
     * 与 {@code /admin/rag-kbs/{tenantCode}/...} 对齐：路径中的租户编码须与当前 {@link TenantContextHolder} 对应行的
     * {@link SysTenant#getCode} 一致，避免将首段误认为知识库 id。
     */
    public void assertPathTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少路径中的租户编码");
        }
        long tenantId = TenantContextHolder.require().getTenantId();
        SysTenant row = sysTenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无效租户上下文"));
        String code = row.getCode();
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "租户未配置编码");
        }
        if (!code.trim().equals(tenantCode.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "路径租户编码与当前工作区不一致");
        }
    }

    public List<RagKbAdminView> list() {
        long tenantId = TenantContextHolder.require().getTenantId();
        return ragKnowledgeBaseRepository.listByTenant(tenantId).stream().map(this::toView).toList();
    }

    /** 管理端展示：是否已接入 Milvus（与 {@code ai.providers.vector-store} 一致）。 */
    public boolean ragCapabilitiesVectorMilvus() {
        return ragVectorInfrastructure.isMilvusVectorStore();
    }

    /**
     * 管理端：按当前 {@code ai.rag.retrieval-mode} 对指定知识库做检索试跑（与对话 RAG 同路径）。
     */
    public RagRetrievalTestView testRetrieval(long kbId, RagRetrievalTestRequest req) {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        String query = req.getQuery() == null ? "" : req.getQuery().trim();
        if (query.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query required");
        }
        int topK = req.getTopK() == null ? 8 : req.getTopK();
        if (topK < 1) {
            topK = 1;
        }
        if (topK > 20) {
            topK = 20;
        }
        RagRetrievalTestSearchResult search =
                ragQueryBridgeService.searchForRetrievalTest(tenantId, kbId, query, topK);
        RagRetrievalTestDiagnostics diag = search.diagnostics();
        List<RagRetrievalTestHitView> hitViews =
                search.hits().stream()
                        .map(
                                h -> {
                                    var c = h.citation();
                                    return new RagRetrievalTestHitView(
                                            c.documentId(),
                                            c.documentTitle(),
                                            c.chunkId(),
                                            c.chunkSeq(),
                                            c.contentPreview(),
                                            h.source() == null ? null : h.source().getCode(),
                                            h.vectorSimilarity(),
                                            h.keywordScore());
                                })
                        .toList();
        return new RagRetrievalTestView(
                tenantRagRuntimeResolver.resolveRetrievalModeStorage(tenantId),
                query,
                topK,
                hitViews.size(),
                hitViews,
                search.snippets(),
                diag.milvusRecallCount(),
                diag.afterCosineThresholdCount(),
                diag.minCosineThreshold(),
                diag.maxMilvusSimilarity(),
                diag.hint());
    }

    public RagKbAdminView create(CreateRagKbRequest req) {
        return toView(ragApplicationService.createKb(req.getName().trim()));
    }

    public RagKbAdminView update(long id, UpdateRagKbRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        var row = requireKb(id, tenantId);
        row.setName(req.getName().trim());
        ragKnowledgeBaseRepository.updateById(row);
        return toView(requireKb(id, tenantId));
    }

    public RagKbAdminView patchSettings(long id, RagKbSettingsPatchRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        RagKnowledgeBase row = requireKb(id, tenantId);
        if (req.getName() != null && !req.getName().isBlank()) {
            row.setName(req.getName().trim());
        }
        if (req.getDefaultChunkStrategyCode() != null) {
            row.setDefaultChunkStrategy(RagChunkStrategy.fromCode(req.getDefaultChunkStrategyCode()));
        }
        if (req.getChunkFixedChars() != null && req.getChunkFixedChars() > 0) {
            row.setChunkFixedChars(req.getChunkFixedChars());
        }
        if (req.getChunkSlideOverlap() != null && req.getChunkSlideOverlap() >= 0) {
            row.setChunkSlideOverlap(req.getChunkSlideOverlap());
        }
        if (Boolean.TRUE.equals(req.getClearAssignedLlmModel())) {
            row.setAssignedLlmModelId(null);
        } else if (req.getAssignedLlmModelId() != null) {
            SysLlmModel assigned =
                    sysLlmModelRepository
                            .findById(tenantId, req.getAssignedLlmModelId())
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST, "未找到该租户下的模型配置"));
            LlmModelKind k = assigned.getModelKind() != null ? assigned.getModelKind() : LlmModelKind.LANGUAGE;
            if (k != LlmModelKind.LANGUAGE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识库对话绑定仅允许「语言」类型模型");
            }
            row.setAssignedLlmModelId(req.getAssignedLlmModelId());
        }
        if (Boolean.TRUE.equals(req.getClearAssignedEmbeddingModel())) {
            row.setAssignedEmbeddingModelId(null);
        } else if (req.getAssignedEmbeddingModelId() != null) {
            SysLlmModel emb =
                    sysLlmModelRepository
                            .findById(tenantId, req.getAssignedEmbeddingModelId())
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST, "未找到该租户下的向量模型配置"));
            LlmModelKind ek = emb.getModelKind() != null ? emb.getModelKind() : LlmModelKind.LANGUAGE;
            if (ek != LlmModelKind.VECTOR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识库嵌入绑定仅允许「向量」类型模型");
            }
            if (emb.getStatus() != LlmModelStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选向量模型未启用");
            }
            row.setAssignedEmbeddingModelId(req.getAssignedEmbeddingModelId());
        }
        if (req.getChatRetrievalEnabled() != null) {
            if (Boolean.TRUE.equals(req.getChatRetrievalEnabled())) {
                ragVectorInfrastructure.assertMilvusOrThrow();
                if (row.getAssignedEmbeddingModelId() == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "开启对话检索前请先绑定「向量模型」（嵌入）");
                }
            }
            row.setChatRetrievalEnabled(
                    Boolean.TRUE.equals(req.getChatRetrievalEnabled()) ? ToggleState.ON : ToggleState.OFF);
        }
        if (req.getChatVectorMinCosineScore() != null) {
            row.setChatVectorMinCosineScore(req.getChatVectorMinCosineScore());
        }
        ragKnowledgeBaseRepository.updateById(row);
        return toView(requireKb(id, tenantId));
    }

    public void delete(long id) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(id, tenantId);
        if (ragDocumentRepository.countActiveByKb(tenantId, id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "rag kb has linked documents");
        }
        ragKbDocumentCategoryRepository.deleteByKb(tenantId, id);
        ragKnowledgeBaseRepository.deleteByIdAndTenant(id, tenantId);
    }

    public List<RagDocumentAdminView> listDocuments(long kbId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        List<RagDocument> rows = ragDocumentRepository.listActiveByKbId(tenantId, kbId);
        return mapDocViews(tenantId, kbId, rows);
    }

    public RagDocumentAdminView getDocument(long kbId, long documentId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not in knowledge base");
        }
        RagDocument doc = ragDocumentRepository.findByIdAndTenant(documentId, tenantId);
        if (doc == null || Objects.equals(doc.getDeleted(), 1)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        return mapDocViews(tenantId, kbId, List.of(doc)).getFirst();
    }

    public Page<RagDocumentAdminView> pageDocuments(
            long kbId, long page, long size, Long categoryId, String displayStatus, String titleKeyword) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        RagDocumentDisplayStatus st = parseDisplayStatusOrNull(displayStatus);
        Page<RagDocument> src =
                ragDocumentRepository.pageByKb(tenantId, kbId, categoryId, st, titleKeyword, page, size);
        Page<RagDocumentAdminView> out = new Page<>(src.getCurrent(), src.getSize(), src.getTotal());
        out.setRecords(mapDocViews(tenantId, kbId, src.getRecords()));
        return out;
    }

    public List<RagDocumentCategoryAdminView> listDocumentCategories(long kbId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        ragApplicationService.ensureDefaultDocCategories(tenantId, kbId);
        return ragKbDocumentCategoryRepository.listByKb(tenantId, kbId).stream().map(this::toCategoryView).toList();
    }

    public RagDocumentCategoryAdminView createDocumentCategory(long kbId, CreateRagDocumentCategoryRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        var c = new RagKbDocumentCategory();
        c.setTenantId(tenantId);
        c.setKbId(kbId);
        c.setName(req.getName().trim());
        c.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        ragKbDocumentCategoryRepository.insert(c);
        return toCategoryView(
                ragKbDocumentCategoryRepository.findById(tenantId, c.getId()).orElseThrow());
    }

    public RagDocumentCategoryAdminView updateDocumentCategory(
            long kbId, long categoryId, UpdateRagDocumentCategoryRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        RagKbDocumentCategory c =
                ragKbDocumentCategoryRepository
                        .findById(tenantId, categoryId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found"));
        if (!Objects.equals(c.getKbId(), kbId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found");
        }
        c.setName(req.getName().trim());
        if (req.getSortOrder() != null) {
            c.setSortOrder(req.getSortOrder());
        }
        ragKbDocumentCategoryRepository.updateById(c);
        return toCategoryView(
                ragKbDocumentCategoryRepository.findById(tenantId, categoryId).orElseThrow());
    }

    public void deleteDocumentCategory(long kbId, long categoryId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        RagKbDocumentCategory c =
                ragKbDocumentCategoryRepository
                        .findById(tenantId, categoryId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found"));
        if (!Objects.equals(c.getKbId(), kbId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found");
        }
        long n = ragDocumentRepository.countActiveByKbAndCategory(tenantId, kbId, categoryId);
        if (n > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "category has documents");
        }
        ragKbDocumentCategoryRepository.delete(tenantId, categoryId);
    }

    public RagDocumentAdminView patchDocument(long kbId, long documentId, PatchRagDocumentRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not in knowledge base");
        }
        RagDocument doc = ragDocumentRepository.findByIdAndTenant(documentId, tenantId);
        if (doc == null || Objects.equals(doc.getDeleted(), 1)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        if (Boolean.TRUE.equals(req.getClearCategory())) {
            doc.setCategoryId(null);
        } else if (req.getCategoryId() != null) {
            RagKbDocumentCategory cat =
                    ragKbDocumentCategoryRepository
                            .findById(tenantId, req.getCategoryId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid category"));
            if (!Objects.equals(cat.getKbId(), kbId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid category");
            }
            doc.setCategoryId(req.getCategoryId());
        }
        if (req.getDisplayStatus() != null && !req.getDisplayStatus().isBlank()) {
            doc.setDisplayStatus(RagDocumentDisplayStatus.fromCode(req.getDisplayStatus()));
        }
        if (req.getApplicableScope() != null) {
            doc.setApplicableScope(req.getApplicableScope().trim());
        }
        ragDocumentRepository.updateById(doc);
        RagDocument saved = ragDocumentRepository.findByIdAndTenant(documentId, tenantId);
        return mapDocViews(tenantId, kbId, List.of(saved)).getFirst();
    }

    public String exportDocumentMarkdown(long kbId, long documentId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not in knowledge base");
        }
        RagDocument doc = ragDocumentRepository.findByIdAndTenant(documentId, tenantId);
        if (doc == null || Objects.equals(doc.getDeleted(), 1)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        String md = doc.getMdContent();
        return md == null ? "" : md;
    }

    public void deleteDocument(long kbId, long documentId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not in knowledge base");
        }
        RagDocument doc = ragDocumentRepository.findByIdAndTenant(documentId, tenantId);
        if (doc == null || Objects.equals(doc.getDeleted(), 1)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        ragDocumentChunkPurgeService.purgeAllChunksForDocument(tenantId, kbId, documentId);
        ragWebCrawlUrlItemRepository.markDeletedByDocumentIds(tenantId, List.of(documentId));
        lnkRagKbDocumentRepository.deleteLink(kbId, documentId);
        doc.setDeleted(1);
        doc.setDeletedAt(BeijingTime.nowLocal());
        ragDocumentRepository.updateById(doc);
    }

    public RagDocumentChunksListResponse listChunks(long kbId, long documentId) {
        List<RagChunkAdminView> views = listChunkViews(kbId, documentId);
        int parents = 0;
        int children = 0;
        int flat = 0;
        for (RagChunkAdminView v : views) {
            String role = v.chunkRole() != null ? v.chunkRole() : "FLAT";
            switch (role) {
                case "PARENT" -> parents++;
                case "CHILD" -> children++;
                default -> flat++;
            }
        }
        boolean parentChild = children > 0 || parents > 0;
        return new RagDocumentChunksListResponse(views, parentChild, parents, children, flat);
    }

    private List<RagChunkAdminView> listChunkViews(long kbId, long documentId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not in knowledge base");
        }
        List<LnkRagDocumentChunk> links = lnkRagDocumentChunkRepository.listByDocumentId(documentId);
        List<Long> ids = links.stream().map(LnkRagDocumentChunk::getChunkId).toList();
        List<RagChunk> chunks = ragChunkRepository.listActiveByIdsOrdered(tenantId, ids);
        var byId = new java.util.HashMap<Long, RagChunk>();
        for (RagChunk c : chunks) {
            byId.put(c.getId(), c);
        }
        List<RagChunkAdminView> out = new ArrayList<>();
        for (LnkRagDocumentChunk l : links) {
            RagChunk c = byId.get(l.getChunkId());
            if (c == null || Objects.equals(c.getDeleted(), 1)) {
                continue;
            }
            out.add(toChunkAdminView(c, l.getSeq() != null ? l.getSeq() : 0));
        }
        out.sort(Comparator.comparingInt(RagChunkAdminView::seq));
        return out;
    }

    public RagChunkAdminView updateChunk(long kbId, long documentId, long chunkId, RagChunkUpdateRequest body) {
        RagChunkPatchRequest p = new RagChunkPatchRequest();
        p.setContent(body.getContent());
        return patchChunk(kbId, documentId, chunkId, p);
    }

    public RagChunkAdminView patchChunk(long kbId, long documentId, long chunkId, RagChunkPatchRequest req) {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not in knowledge base");
        }
        RagChunk ch = requireLinkedChunk(kbId, documentId, chunkId, tenantId);
        boolean touched = false;
        if (req.getContent() != null && !req.getContent().isBlank()) {
            ch.setContent(req.getContent().trim());
            touched = true;
        }
        if (req.getRetrievalEnabled() != null && !req.getRetrievalEnabled().isBlank()) {
            ch.setRetrievalEnabled(RagChunkRetrievalEnabled.fromCode(req.getRetrievalEnabled()));
            touched = true;
        }
        if (!touched) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no patch fields");
        }
        String ref =
                ch.getEmbeddingRef() != null && !ch.getEmbeddingRef().isBlank()
                        ? ch.getEmbeddingRef()
                        : String.valueOf(ch.getId());
        ch.setEmbeddingRef(ref);
        ragChunkRepository.updateById(ch);
        syncChunkVector(tenantId, kbId, ragChunkRepository.findByIdAndTenant(chunkId, tenantId), ref);
        RagChunk updated = ragChunkRepository.findByIdAndTenant(chunkId, tenantId);
        int seq = resolveChunkSeq(documentId, chunkId);
        return toChunkAdminView(updated, seq);
    }

    public void deleteChunk(long kbId, long documentId, long chunkId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not in knowledge base");
        }
        RagChunk ch = ragChunkRepository.findByIdAndTenant(chunkId, tenantId);
        if (ch == null || Objects.equals(ch.getDeleted(), 1)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "chunk not found");
        }
        boolean linked =
                lnkRagDocumentChunkRepository.listByDocumentId(documentId).stream()
                        .anyMatch(l -> l.getChunkId().equals(chunkId));
        if (!linked) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "chunk not linked to document");
        }
        List<Long> toDelete = new ArrayList<>();
        toDelete.add(chunkId);
        if (ch.getParentChunkId() == null
                && ch.getRetrievalEnabled() == RagChunkRetrievalEnabled.DISABLED) {
            toDelete.addAll(ragChunkRepository.listChildChunkIds(tenantId, chunkId));
        }
        List<String> refs = new ArrayList<>();
        for (Long id : toDelete) {
            RagChunk row = ragChunkRepository.findByIdAndTenant(id, tenantId);
            if (row == null || Objects.equals(row.getDeleted(), 1)) {
                continue;
            }
            String ref =
                    row.getEmbeddingRef() != null && !row.getEmbeddingRef().isBlank()
                            ? row.getEmbeddingRef()
                            : String.valueOf(row.getId());
            if (row.getRetrievalEnabled() == RagChunkRetrievalEnabled.ENABLED) {
                refs.add(ref);
            }
            lnkRagDocumentChunkRepository.deleteByDocumentIdAndChunkId(documentId, id);
        }
        if (!refs.isEmpty()) {
            vectorStorePort.deleteChunkVectors(tenantId, "kb_" + kbId, refs);
        }
        ragChunkRepository.markDeleted(tenantId, toDelete);
        resequenceDocumentChunks(documentId);
    }

    public RagChunkAdminView createChunk(long kbId, long documentId, CreateRagChunkRequest req) {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not in knowledge base");
        }
        List<LnkRagDocumentChunk> links = lnkRagDocumentChunkRepository.listByDocumentId(documentId);
        int nextSeq =
                links.stream()
                        .mapToInt(l -> l.getSeq() != null ? l.getSeq() : 0)
                        .max()
                        .orElse(-1)
                        + 1;
        RagChunk ch = new RagChunk();
        ch.setTenantId(tenantId);
        ch.setDeleted(0);
        ch.setContent(req.getContent().trim());
        ch.setRetrievalEnabled(RagChunkRetrievalEnabled.ENABLED);
        ragChunkRepository.insert(ch);
        LnkRagDocumentChunk lnkDc = new LnkRagDocumentChunk();
        lnkDc.setDocumentId(documentId);
        lnkDc.setChunkId(ch.getId());
        lnkDc.setSeq(nextSeq);
        lnkRagDocumentChunkRepository.insert(lnkDc);
        String ref = String.valueOf(ch.getId());
        ch.setEmbeddingRef(ref);
        ragChunkRepository.updateById(ch);
        float[] vec = ragEmbeddingPort.embed(tenantId, kbId, ch.getContent());
        vectorStorePort.upsertChunks(tenantId, "kb_" + kbId, List.of(ref), List.of(vec));
        RagChunk saved = ragChunkRepository.findByIdAndTenant(ch.getId(), tenantId);
        return toChunkAdminView(saved, nextSeq);
    }

    public RagChunkAdminView mergeChunkWithNext(long kbId, long documentId, long chunkId) {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        List<RagChunkAdminView> ordered = listChunkViews(kbId, documentId);
        int idx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id() == chunkId) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "chunk not found");
        }
        if (idx >= ordered.size() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no next chunk to merge");
        }
        long nextId = ordered.get(idx + 1).id();
        RagChunk cur = requireLinkedChunk(kbId, documentId, chunkId, tenantId);
        RagChunk nxt = requireLinkedChunk(kbId, documentId, nextId, tenantId);
        String merged = cur.getContent().trim() + "\n\n" + nxt.getContent().trim();
        cur.setContent(merged);
        ragChunkRepository.updateById(cur);
        String nextRef =
                nxt.getEmbeddingRef() != null && !nxt.getEmbeddingRef().isBlank()
                        ? nxt.getEmbeddingRef()
                        : String.valueOf(nxt.getId());
        vectorStorePort.deleteChunkVectors(tenantId, "kb_" + kbId, List.of(nextRef));
        lnkRagDocumentChunkRepository.deleteByDocumentIdAndChunkId(documentId, nextId);
        ragChunkRepository.markDeleted(tenantId, List.of(nextId));
        String curRef =
                cur.getEmbeddingRef() != null && !cur.getEmbeddingRef().isBlank()
                        ? cur.getEmbeddingRef()
                        : String.valueOf(cur.getId());
        cur.setEmbeddingRef(curRef);
        ragChunkRepository.updateById(cur);
        RagChunk updated = ragChunkRepository.findByIdAndTenant(chunkId, tenantId);
        syncChunkVector(tenantId, kbId, updated, curRef);
        resequenceDocumentChunks(documentId);
        RagChunk after = ragChunkRepository.findByIdAndTenant(chunkId, tenantId);
        return toChunkAdminView(after, resolveChunkSeq(documentId, chunkId));
    }

    private RagChunk requireLinkedChunk(long kbId, long documentId, long chunkId, long tenantId) {
        if (!lnkRagKbDocumentRepository.existsKbDocument(kbId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not in knowledge base");
        }
        RagChunk ch = ragChunkRepository.findByIdAndTenant(chunkId, tenantId);
        if (ch == null || Objects.equals(ch.getDeleted(), 1)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "chunk not found");
        }
        boolean linked =
                lnkRagDocumentChunkRepository.listByDocumentId(documentId).stream()
                        .anyMatch(l -> l.getChunkId().equals(chunkId));
        if (!linked) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "chunk not linked to document");
        }
        return ch;
    }

    private int resolveChunkSeq(long documentId, long chunkId) {
        return lnkRagDocumentChunkRepository.listByDocumentId(documentId).stream()
                .filter(l -> l.getChunkId().equals(chunkId))
                .map(LnkRagDocumentChunk::getSeq)
                .findFirst()
                .orElse(0);
    }

    private void resequenceDocumentChunks(long documentId) {
        List<LnkRagDocumentChunk> links = lnkRagDocumentChunkRepository.listByDocumentId(documentId);
        int n = 0;
        for (LnkRagDocumentChunk l : links) {
            if (l.getSeq() == null || l.getSeq() != n) {
                l.setSeq(n);
                lnkRagDocumentChunkRepository.updateById(l);
            }
            n++;
        }
    }

    private void syncChunkVector(long tenantId, long kbId, RagChunk ch, String ref) {
        if (ch == null) {
            return;
        }
        RagChunkRetrievalEnabled en =
                ch.getRetrievalEnabled() != null ? ch.getRetrievalEnabled() : RagChunkRetrievalEnabled.ENABLED;
        if (en == RagChunkRetrievalEnabled.DISABLED) {
            vectorStorePort.deleteChunkVectors(tenantId, "kb_" + kbId, List.of(ref));
        } else {
            float[] vec = ragEmbeddingPort.embed(tenantId, kbId, ch.getContent());
            vectorStorePort.upsertChunks(tenantId, "kb_" + kbId, List.of(ref), List.of(vec));
        }
    }

    private RagChunkAdminView toChunkAdminView(RagChunk c, int seq) {
        RagChunkRetrievalEnabled en =
                c.getRetrievalEnabled() != null ? c.getRetrievalEnabled() : RagChunkRetrievalEnabled.ENABLED;
        String content = c.getContent() != null ? c.getContent() : "";
        long hits = c.getHitCount() == null ? 0L : c.getHitCount();
        String role = resolveChunkRole(c);
        return new RagChunkAdminView(
                c.getId(),
                seq,
                content,
                c.getEmbeddingRef(),
                en.getApiCode(),
                content.length(),
                hits,
                c.getParentChunkId(),
                role,
                c.getCreatedAt() != null ? ISO.format(c.getCreatedAt()) : null,
                c.getUpdatedAt() != null ? ISO.format(c.getUpdatedAt()) : null);
    }

    private static String resolveChunkRole(RagChunk c) {
        if (c.getParentChunkId() != null) {
            return "CHILD";
        }
        if (c.getRetrievalEnabled() == RagChunkRetrievalEnabled.DISABLED) {
            return "PARENT";
        }
        return "FLAT";
    }

    public RagDocumentUploadResponse uploadDocument(
            long kbId, MultipartFile file, Integer chunkStrategy, Long categoryId)
            throws Exception {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        ragKbVectorModelGuard.assertKbHasVectorEmbeddingModel(tenantId, kbId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file");
        }
        if (file.getSize() > UPLOAD_MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file too large");
        }
        String extracted;
        try {
            extracted =
                    documentTextExtractor.extractFromMultipart(
                            file, TikaDocumentTextExtractor.MAX_CHARS_RAG_UPLOAD);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to parse file: " + e.getMessage());
        }
        if (extracted == null || extracted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no extractable text");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        RagIngestOrchestrationService.PersistResult pr =
                ragIngestOrchestrationService.ingestUploadedMarkdownSync(
                        tenantId, kbId, extracted, name, chunkStrategy, categoryId);
        return new RagDocumentUploadResponse(pr.documentId(), pr.chunkCount());
    }

    public long enqueueIndexJob(long kbId) throws Exception {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        return ragApplicationService.enqueueIndexJob(kbId);
    }

    public long enqueueUrlImportJob(long kbId, UrlImportJobRequest body) throws Exception {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        String url = body.getUrl().trim();
        assertHttpUrl(url);
        return ragApplicationService.enqueueUrlImportJob(
                kbId, url, body.getChunkStrategy(), body.getCategoryId());
    }

    public com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewView previewIngestChunks(
            long kbId, com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewRequest body) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        return ragIngestPreviewApplicationService.preview(tenantId, kbId, body);
    }

    public IngestAnalyzeView analyzeIngestContent(String markdown) {
        var r = RagDocumentChunkProfileAnalyzer.analyze(markdown);
        return new IngestAnalyzeView(
                r.suggestParentChild(),
                r.charCount(),
                r.majorHeadingCount(),
                r.minorHeadingCount(),
                r.reasons());
    }

    public IngestAnalyzeView analyzeIngestUpload(long kbId, MultipartFile file) throws Exception {
        requireKb(kbId, TenantContextHolder.require().getTenantId());
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file");
        }
        String extracted =
                documentTextExtractor.extractFromMultipart(
                        file, TikaDocumentTextExtractor.MAX_CHARS_RAG_UPLOAD);
        if (extracted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no extractable text");
        }
        return analyzeIngestContent(extracted);
    }

    public long enqueueFileImportJob(long kbId, FileIngestJobRequest body) throws Exception {
        ragVectorInfrastructure.assertMilvusOrThrow();
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        return ragApplicationService.enqueueFileImportJob(
                kbId,
                body.getOriginalFilename(),
                body.getContentType(),
                body.getMarkdownContent(),
                body.getChunkStrategy(),
                body.getCategoryId());
    }

    private static void assertHttpUrl(String url) {
        URI u = URI.create(url);
        String scheme = u.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only http(s) url allowed");
        }
    }

    private RagKnowledgeBase requireKb(long id, long tenantId) {
        var row = ragKnowledgeBaseRepository.findByIdAndTenant(id, tenantId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "rag knowledge base not found");
        }
        return row;
    }

    private RagKbAdminView toView(RagKnowledgeBase kb) {
        RagChunkStrategy st =
                kb.getDefaultChunkStrategy() != null ? kb.getDefaultChunkStrategy() : RagChunkStrategy.FIXED_CHAR;
        int fixed = kb.getChunkFixedChars() != null ? kb.getChunkFixedChars() : 800;
        int slide = kb.getChunkSlideOverlap() != null ? kb.getChunkSlideOverlap() : 120;
        ToggleState chatRag =
                kb.getChatRetrievalEnabled() != null ? kb.getChatRetrievalEnabled() : ToggleState.ON;
        double minCos =
                kb.getChatVectorMinCosineScore() != null ? kb.getChatVectorMinCosineScore() : 0.65d;
        return new RagKbAdminView(
                kb.getId(),
                kb.getTenantId(),
                kb.getName(),
                st.name(),
                fixed,
                slide,
                kb.getAssignedLlmModelId(),
                kb.getAssignedEmbeddingModelId(),
                chatRag.name(),
                minCos,
                kb.getCreatedAt() != null ? ISO.format(kb.getCreatedAt()) : null,
                kb.getUpdatedAt() != null ? ISO.format(kb.getUpdatedAt()) : null);
    }

    private List<RagDocumentAdminView> mapDocViews(long tenantId, long kbId, List<RagDocument> rows) {
        List<RagKbDocumentCategory> cats = ragKbDocumentCategoryRepository.listByKb(tenantId, kbId);
        Map<Long, String> catNames =
                cats.stream()
                        .collect(
                                Collectors.toMap(
                                        RagKbDocumentCategory::getId,
                                        RagKbDocumentCategory::getName,
                                        (a, b) -> a));
        Set<Long> userIds =
                rows.stream()
                        .map(RagDocument::getUploadedByUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Long, String> userLabels = loadUserLoginLabels(userIds);
        return rows.stream().map(d -> toDocView(d, catNames, userLabels)).toList();
    }

    private Map<Long, String> loadUserLoginLabels(Set<Long> userIds) {
        Map<Long, String> m = new HashMap<>();
        for (Long id : userIds) {
            if (id == null) {
                continue;
            }
            secUserAccountRepository
                    .findById(id)
                    .ifPresent(acc -> m.put(id, acc.getLoginName() != null ? acc.getLoginName() : String.valueOf(id)));
        }
        return m;
    }

    private RagDocumentAdminView toDocView(
            RagDocument d, Map<Long, String> categoryNamesById, Map<Long, String> userLoginById) {
        Long catId = d.getCategoryId();
        String catName = catId == null ? null : categoryNamesById.get(catId);
        RagDocumentDisplayStatus dst =
                d.getDisplayStatus() != null ? d.getDisplayStatus() : RagDocumentDisplayStatus.PUBLISHED;
        Long uid = d.getUploadedByUserId();
        String ulabel = null;
        if (uid != null) {
            ulabel = userLoginById.getOrDefault(uid, String.valueOf(uid));
        }
        long docHits = d.getHitCount() == null ? 0L : d.getHitCount();
        return new RagDocumentAdminView(
                d.getId(),
                d.getTenantId(),
                d.getTitle(),
                d.getSourceType() != null ? d.getSourceType().getCode() : "",
                d.getSourceUri(),
                d.getOriginalFilename(),
                d.getContentLength() != null ? d.getContentLength() : 0L,
                catId,
                catName,
                dst.getCode(),
                d.getApplicableScope(),
                uid,
                ulabel,
                docHits,
                d.getCreatedAt() != null ? ISO.format(d.getCreatedAt()) : null,
                d.getUpdatedAt() != null ? ISO.format(d.getUpdatedAt()) : null);
    }

    private RagDocumentCategoryAdminView toCategoryView(RagKbDocumentCategory c) {
        return new RagDocumentCategoryAdminView(
                c.getId(),
                c.getKbId(),
                c.getName(),
                c.getSortOrder() == null ? 0 : c.getSortOrder(),
                c.getCreatedAt() != null ? ISO.format(c.getCreatedAt()) : null,
                c.getUpdatedAt() != null ? ISO.format(c.getUpdatedAt()) : null);
    }

    private static RagDocumentDisplayStatus parseDisplayStatusOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return RagDocumentDisplayStatus.fromCode(raw);
    }
}
