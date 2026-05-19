package com.aaron.cloud.rag.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.rag.RagKbAdminApplicationService;
import com.aaron.cloud.rag.RagWebCrawlAdminApplicationService;
import com.aaron.cloud.rag.RagWebCrawlSiteAdminApplicationService;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.CreateRagChunkRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.CreateRagDocumentCategoryRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.CreateRagKbRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.FileIngestJobRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.PatchRagDocumentRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.IndexJobResponse;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagChunkAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentChunksListResponse;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagChunkPatchRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentCategoryAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentUploadResponse;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagKbAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagKbSettingsPatchRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.UpdateRagDocumentCategoryRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.UpdateRagKbRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.LocalSiteCrawlRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagWebCrawlSiteAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagWebCrawlSiteUpsertRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagRetrievalTestRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagRetrievalTestView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.UrlImportJobRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class RagKbAdminRestController extends ApiV1ControllerBases.RagKbAdmin {

    private final RagKbAdminApplicationService ragKbAdminApplicationService;
    private final RagWebCrawlAdminApplicationService ragWebCrawlAdminApplicationService;
    private final RagWebCrawlSiteAdminApplicationService ragWebCrawlSiteAdminApplicationService;

    private void requireTenantPath(String tenantCode) {
        ragKbAdminApplicationService.assertPathTenantCode(tenantCode);
    }

    /** 管理端用于展示「向量 / RAG 是否可用」等运行时能力（与 {@code ai.providers.vector-store} 一致）。 */
    @GetMapping("/capabilities")
    public Map<String, Boolean> capabilities(@PathVariable("tenantCode") String tenantCode) {
        requireTenantPath(tenantCode);
        return Map.of("vectorStoreMilvus", ragKbAdminApplicationService.ragCapabilitiesVectorMilvus());
    }

    @GetMapping
    public List<RagKbAdminView> list(@PathVariable("tenantCode") String tenantCode) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RagKbAdminView create(@PathVariable("tenantCode") String tenantCode, @Valid @RequestBody CreateRagKbRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.create(body);
    }

    @PutMapping("/{id}")
    public RagKbAdminView update(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id, @Valid @RequestBody UpdateRagKbRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.update(id, body);
    }

    @PatchMapping("/{id}/settings")
    public RagKbAdminView patchSettings(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @Valid @RequestBody RagKbSettingsPatchRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.patchSettings(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("tenantCode") String tenantCode, @PathVariable long id) {
        requireTenantPath(tenantCode);
        ragKbAdminApplicationService.delete(id);
    }

    @PostMapping("/{id}/index-jobs")
    public IndexJobResponse enqueue(@PathVariable("tenantCode") String tenantCode, @PathVariable long id) throws Exception {
        requireTenantPath(tenantCode);
        return new IndexJobResponse(ragKbAdminApplicationService.enqueueIndexJob(id));
    }

    /** 管理端：按当前检索模式试跑向量/混合召回，与对话 RAG 同路径。 */
    @PostMapping("/{id}/retrieval-test")
    public RagRetrievalTestView retrievalTest(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @Valid @RequestBody RagRetrievalTestRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.testRetrieval(id, body);
    }

    /** 入库前分片预览（不写库、不向量化）。 */
    @PostMapping("/{id}/ingest/preview-chunks")
    public ChunkPreviewView previewIngestChunks(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @RequestBody ChunkPreviewRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.previewIngestChunks(id, body);
    }

    /** 上传类文档入库前分析：是否推荐子母分片（长文 / 多层级标题 / 政策手册形态）。 */
    @PostMapping("/{id}/ingest/analyze")
    public com.aaron.cloud.rag.dto.RagKbAdminDtos.IngestAnalyzeView analyzeIngestMarkdown(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @Valid @RequestBody com.aaron.cloud.rag.dto.RagKbAdminDtos.IngestAnalyzeRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.analyzeIngestContent(
                body.getMarkdownContent() != null ? body.getMarkdownContent() : "");
    }

    @PostMapping(value = "/{id}/ingest/analyze-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public com.aaron.cloud.rag.dto.RagKbAdminDtos.IngestAnalyzeView analyzeIngestUpload(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @RequestPart("file") MultipartFile file)
            throws Exception {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.analyzeIngestUpload(id, file);
    }

    /** 网页导入知识库：入队 {@code RAG_URL_IMPORT}，流水线内完成爬取、MD、分块、向量化（迭代中可扩展）。 */
    @PostMapping("/{id}/url-import-jobs")
    public IndexJobResponse enqueueUrlImport(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @Valid @RequestBody UrlImportJobRequest body)
            throws Exception {
        requireTenantPath(tenantCode);
        return new IndexJobResponse(ragKbAdminApplicationService.enqueueUrlImportJob(id, body));
    }

    /** 文件入知识库：入队 {@code RAG_FILE_IMPORT}；与对象存储上传衔接后扩展解析与向量写入。 */
    @PostMapping("/{id}/file-ingest-jobs")
    public IndexJobResponse enqueueFileIngest(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @Valid @RequestBody FileIngestJobRequest body)
            throws Exception {
        requireTenantPath(tenantCode);
        return new IndexJobResponse(ragKbAdminApplicationService.enqueueFileImportJob(id, body));
    }

    @GetMapping("/{id}/document-categories")
    public List<RagDocumentCategoryAdminView> listDocumentCategories(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.listDocumentCategories(id);
    }

    @PostMapping("/{id}/document-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public RagDocumentCategoryAdminView createDocumentCategory(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @Valid @RequestBody CreateRagDocumentCategoryRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.createDocumentCategory(id, body);
    }

    @PutMapping("/{id}/document-categories/{catId}")
    public RagDocumentCategoryAdminView updateDocumentCategory(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @PathVariable long catId,
            @Valid @RequestBody UpdateRagDocumentCategoryRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.updateDocumentCategory(id, catId, body);
    }

    @DeleteMapping("/{id}/document-categories/{catId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocumentCategory(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id, @PathVariable long catId) {
        requireTenantPath(tenantCode);
        ragKbAdminApplicationService.deleteDocumentCategory(id, catId);
    }

    @GetMapping("/{id}/documents")
    public List<RagDocumentAdminView> listDocuments(@PathVariable("tenantCode") String tenantCode, @PathVariable long id) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.listDocuments(id);
    }

    /** 单文档视图（路径避免与 {@code .../documents/page} 冲突）。 */
    @GetMapping("/{id}/document/{docId}")
    public RagDocumentAdminView getDocument(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id, @PathVariable long docId) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.getDocument(id, docId);
    }

    @GetMapping("/{id}/documents/page")
    public Page<RagDocumentAdminView> pageDocuments(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String displayStatus,
            @RequestParam(required = false) String titleKeyword) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.pageDocuments(id, page, size, categoryId, displayStatus, titleKeyword);
    }

    @PatchMapping("/{id}/documents/{docId}")
    public RagDocumentAdminView patchDocument(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @PathVariable long docId,
            @Valid @RequestBody PatchRagDocumentRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.patchDocument(id, docId, body);
    }

    @GetMapping(value = "/{id}/documents/{docId}/markdown", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportDocumentMarkdown(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id, @PathVariable long docId) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.exportDocumentMarkdown(id, docId);
    }

    @DeleteMapping("/{id}/documents/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id, @PathVariable long docId) {
        requireTenantPath(tenantCode);
        ragKbAdminApplicationService.deleteDocument(id, docId);
    }

    @GetMapping("/{id}/documents/{docId}/chunks")
    public RagDocumentChunksListResponse listChunks(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id, @PathVariable long docId) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.listChunks(id, docId);
    }

    @PostMapping("/{id}/documents/{docId}/chunks")
    @ResponseStatus(HttpStatus.CREATED)
    public RagChunkAdminView createChunk(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @PathVariable long docId,
            @Valid @RequestBody CreateRagChunkRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.createChunk(id, docId, body);
    }

    @PatchMapping("/{id}/documents/{docId}/chunks/{chunkId}")
    public RagChunkAdminView patchChunk(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @PathVariable long docId,
            @PathVariable long chunkId,
            @Valid @RequestBody RagChunkPatchRequest body) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.patchChunk(id, docId, chunkId, body);
    }

    @DeleteMapping("/{id}/documents/{docId}/chunks/{chunkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChunk(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @PathVariable long docId,
            @PathVariable long chunkId) {
        requireTenantPath(tenantCode);
        ragKbAdminApplicationService.deleteChunk(id, docId, chunkId);
    }

    @PostMapping("/{id}/documents/{docId}/chunks/{chunkId}/merge-with-next")
    public RagChunkAdminView mergeChunkWithNext(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @PathVariable long docId,
            @PathVariable long chunkId) {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.mergeChunkWithNext(id, docId, chunkId);
    }

    @GetMapping("/web-crawl/site-meta")
    public Map<String, Object> webCrawlSiteMeta(@PathVariable("tenantCode") String tenantCode) {
        requireTenantPath(tenantCode);
        return ragWebCrawlSiteAdminApplicationService.siteMeta();
    }

    @GetMapping("/{id}/web-crawl/sites")
    public List<RagWebCrawlSiteAdminView> listWebCrawlSites(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id) {
        requireTenantPath(tenantCode);
        return ragWebCrawlSiteAdminApplicationService.list(id);
    }

    @PostMapping("/{id}/web-crawl/sites")
    @ResponseStatus(HttpStatus.CREATED)
    public RagWebCrawlSiteAdminView createWebCrawlSite(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @Valid @RequestBody RagWebCrawlSiteUpsertRequest body)
            throws Exception {
        requireTenantPath(tenantCode);
        return ragWebCrawlSiteAdminApplicationService.create(id, body);
    }

    @PutMapping("/{id}/web-crawl/sites/{siteId}")
    public RagWebCrawlSiteAdminView updateWebCrawlSite(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @PathVariable long siteId,
            @Valid @RequestBody RagWebCrawlSiteUpsertRequest body) {
        requireTenantPath(tenantCode);
        return ragWebCrawlSiteAdminApplicationService.update(id, siteId, body);
    }

    @DeleteMapping("/{id}/web-crawl/sites/{siteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWebCrawlSite(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id, @PathVariable long siteId) {
        requireTenantPath(tenantCode);
        ragWebCrawlSiteAdminApplicationService.delete(id, siteId);
    }

    @PostMapping("/{id}/web-crawl/sites/{siteId}/run")
    public Map<String, Boolean> runWebCrawlSite(
            @PathVariable("tenantCode") String tenantCode, @PathVariable long id, @PathVariable long siteId)
            throws Exception {
        requireTenantPath(tenantCode);
        ragWebCrawlSiteAdminApplicationService.runNow(id, siteId);
        return Map.of("started", true);
    }

    /** 本地规则一条龙网页爬取（一次性；周期爬站配置见 web-crawl/sites）。 */
    @PostMapping("/{id}/web-crawl/local")
    public Map<String, Boolean> submitLocalSiteCrawl(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @Valid @RequestBody LocalSiteCrawlRequest body)
            throws Exception {
        requireTenantPath(tenantCode);
        return Map.of("accepted", ragWebCrawlAdminApplicationService.submitLocalSiteCrawl(id, body));
    }

    @PostMapping(value = "/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RagDocumentUploadResponse upload(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Integer chunkStrategy,
            @RequestParam(required = false) Long categoryId)
            throws Exception {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.uploadDocument(id, file, chunkStrategy, categoryId);
    }
}
