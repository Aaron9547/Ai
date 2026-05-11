package com.aaron.cloud.rag.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.rag.RagKbAdminApplicationService;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.CreateRagChunkRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.CreateRagDocumentCategoryRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.CreateRagKbRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.FileIngestJobRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.PatchRagDocumentRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.IndexJobResponse;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagChunkAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagChunkPatchRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentCategoryAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagDocumentUploadResponse;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagKbAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagKbSettingsPatchRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.UpdateRagDocumentCategoryRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.UpdateRagKbRequest;
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
    public List<RagChunkAdminView> listChunks(
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

    @PostMapping(value = "/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RagDocumentUploadResponse upload(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Integer chunkStrategy)
            throws Exception {
        requireTenantPath(tenantCode);
        return ragKbAdminApplicationService.uploadDocument(id, file, chunkStrategy);
    }
}
