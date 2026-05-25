package com.aaron.cloud.rag.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public final class RagKbAdminDtos {

    private RagKbAdminDtos() {}

    @Data
    public static class CreateRagKbRequest {
        @NotBlank
        @Size(max = 255)
        private String name;
    }

    @Data
    public static class UpdateRagKbRequest {
        @NotBlank
        @Size(max = 255)
        private String name;
    }

    @Data
    public static class RagKbSettingsPatchRequest {
        @Size(max = 255)
        private String name;

        /** 瑕嗙洊 {@link com.aaron.cloud.common.api.enums.RagChunkStrategy} 鐨?{@code code}锛涗笉浼犲垯涓嶄慨鏀广€?*/
        private Integer defaultChunkStrategyCode;

        private Integer chunkFixedChars;
        private Integer chunkSlideOverlap;
        /** 绑定对话侧语言模型 {@code llm_model.id}（须 {@code LANGUAGE}）；传 {@code null} 且 {@code clearAssignedLlmModel=true} 时清空。 */
        private Long assignedLlmModelId;

        private Boolean clearAssignedLlmModel;

        /** 绑定嵌入向量模型 {@code llm_model.id}（须 {@code VECTOR}）；传 {@code null} 且 {@code clearAssignedEmbeddingModel=true} 时清空。 */
        private Long assignedEmbeddingModelId;

        private Boolean clearAssignedEmbeddingModel;

        /** 是否纳入 C 端对话 RAG 检索；不传则不修改。 */
        private Boolean chatRetrievalEnabled;

        /**
         * 对话侧 Milvus COSINE 分数下限（仅作用于本知识库）；不传则不修改。{@code 0} 表示关闭该知识库的向量分数过滤。
         */
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "1.0", inclusive = true)
        private Double chatVectorMinCosineScore;
    }

    public record RagKbAdminView(
            long id,
            long tenantId,
            String name,
            String defaultChunkStrategy,
            int chunkFixedChars,
            int chunkSlideOverlap,
            Long assignedLlmModelId,
            Long assignedEmbeddingModelId,
            String chatRetrievalEnabled,
            double chatVectorMinCosineScore,
            String createdAt,
            String updatedAt) {}

    public record RagDocumentAdminView(
            long id,
            long tenantId,
            String title,
            String sourceType,
            String sourceUri,
            String originalFilename,
            long contentLength,
            Long categoryId,
            String categoryName,
            String displayStatus,
            String applicableScope,
            Long uploadedByUserId,
            String uploadedByLabel,
            long hitCount,
            String createdAt,
            String updatedAt) {}

    public record RagDocumentCategoryAdminView(
            long id, long kbId, String name, int sortOrder, String createdAt, String updatedAt) {}

    @Data
    public static class CreateRagDocumentCategoryRequest {
        @NotBlank
        @Size(max = 128)
        private String name;

        private Integer sortOrder;
    }

    @Data
    public static class UpdateRagDocumentCategoryRequest {
        @NotBlank
        @Size(max = 128)
        private String name;

        private Integer sortOrder;
    }

    @Data
    public static class PatchRagDocumentRequest {
        private Long categoryId;
        private Boolean clearCategory;
        private String displayStatus;
        @Size(max = 512)
        private String applicableScope;
    }

    public record RagChunkAdminView(
            long id,
            int seq,
            String content,
            String embeddingRef,
            String retrievalEnabled,
            int contentLength,
            long hitCount,
            Long parentChunkId,
            /** FLAT=普通分片；PARENT=子母分片之母块（不参与检索）；CHILD=子母分片之子块。 */
            String chunkRole,
            String createdAt,
            String updatedAt) {}

    /** 文档分片列表（含子母结构统计，供管理端分片页切换视图）。 */
    public record RagDocumentChunksListResponse(
            List<RagChunkAdminView> chunks,
            boolean parentChild,
            int parentCount,
            int childCount,
            int flatCount) {}

    @Data
    public static class RagChunkUpdateRequest {
        @NotBlank
        @Size(max = 200_000)
        private String content;
    }

    @Data
    public static class RagChunkPatchRequest {
        @Size(max = 200_000)
        private String content;

        /** {@link com.aaron.cloud.common.api.enums.RagChunkRetrievalEnabled} 鐨勬灇涓惧悕锛屽 ENABLED銆丏ISABLED銆?*/
        private String retrievalEnabled;
    }

    @Data
    public static class CreateRagChunkRequest {
        @NotBlank
        @Size(max = 200_000)
        private String content;
    }

    public record RagDocumentUploadResponse(long documentId, int chunkCount) {}

    public record IngestAnalyzeView(
            boolean suggestParentChild,
            int charCount,
            int majorHeadingCount,
            int minorHeadingCount,
            java.util.List<String> reasons) {}

    @Data
    public static class IngestAnalyzeRequest {
        @Size(max = 1_000_000)
        private String markdownContent;
    }

    public record IndexJobResponse(long jobTaskId) {}

    @Data
    public static class UrlImportJobRequest {
        @NotBlank
        @Size(max = 2048)
        @URL
        private String url;

        /** 鍙€夛細鏈浠诲姟瑕嗙洊鍒嗙墖绛栫暐锛坽@link com.aaron.cloud.common.api.enums.RagChunkStrategy} 鐨?code锛夈€?*/
        private Integer chunkStrategy;

        /** 可选：入库后归属的文档分类 ID。 */
        private Long categoryId;
    }

    @Data
    public static class FileIngestJobRequest {
        @NotBlank
        @Size(max = 512)
        private String originalFilename;

        @Size(max = 255)
        private String contentType;

        /** 鍙€夛細鐩存帴绮樿创鐨?Markdown锛涗笌銆屾枃浠朵笂浼犮€嶆帴鍙ｄ簩閫変竴鎴栧悓鏃剁敤浜庡紓姝ヤ换鍔°€?*/
        @Size(max = 1_000_000)
        private String markdownContent;

        /** 鍙€夛細鏈浠诲姟瑕嗙洊鍒嗙墖绛栫暐锛坽@link com.aaron.cloud.common.api.enums.RagChunkStrategy} 鐨?code锛夈€?*/
        private Integer chunkStrategy;

        /** 可选：入库后归属的文档分类 ID。 */
        private Long categoryId;
    }

    @Data
    public static class LocalSiteCrawlRequest {
        @NotBlank
        @Size(max = 2048)
        @URL
        private String baseUrl;

        /** {@link com.aaron.cloud.common.api.enums.RagWebCrawlSyncMode} code */
        private String syncMode;

        private Integer maxDepth;

        /** 默认 true：过滤租户内已爬 URL */
        private Boolean filterCrawled;

        private Integer chunkStrategy;
        private Long categoryId;

        /** 默认 true：入队 RAG_SITE_CRAWL 异步任务 */
        private Boolean asyncJob;
    }

    @Data
    public static class RagRetrievalTestRequest {
        @NotBlank
        @Size(max = 2000)
        private String query;

        /** 返回条数，默认 8，最大 20。 */
        private Integer topK;
    }

    public record RagRetrievalTestHitView(
            long documentId, String documentTitle, long chunkId, int chunkSeq, String contentPreview) {}

    public record RagRetrievalTestView(
            String retrievalMode,
            String query,
            int topK,
            int hitCount,
            java.util.List<RagRetrievalTestHitView> hits,
            java.util.List<String> snippets,
            int milvusRecallCount,
            int afterCosineThresholdCount,
            double minCosineThreshold,
            String diagnosticsHint) {}

    @Data
    public static class RagWebCrawlSiteUpsertRequest {
        @NotBlank
        @Size(max = 128)
        private String name;

        @NotBlank
        @Size(max = 2048)
        @URL
        private String baseUrl;

        @NotBlank
        private String schedulePreset;

        @Size(max = 8)
        private String runAtTime;

        private Boolean enabled;
        private Long categoryId;
        private Integer chunkStrategy;
        private String syncMode;
        private Integer maxDepth;
        private Boolean filterCrawled;

        private com.aaron.cloud.rag.RagWebCrawlExtractConfig extractConfig;
    }

    public record RagWebCrawlSiteAdminView(
            long id,
            long kbId,
            String name,
            String baseUrl,
            String schedulePreset,
            String schedulePresetLabel,
            String runAtTime,
            boolean enabled,
            boolean firstRunDone,
            String lastCrawlAt,
            Long categoryId,
            Integer chunkStrategy,
            String syncMode,
            String syncModeLabel,
            Integer maxDepth,
            boolean filterCrawled,
            com.aaron.cloud.rag.RagWebCrawlExtractConfig extractConfig,
            String createdAt,
            String updatedAt) {}

    @Data
    public static class ChunkPreviewRequest {
        @Size(max = 2048)
        private String url;

        @Size(max = 2048)
        private String baseUrl;

        private Integer maxDepth;
        private Integer chunkStrategy;
        private Long siteId;
        private com.aaron.cloud.rag.RagWebCrawlExtractConfig extractConfig;
    }

    public record ChunkPreviewChunkView(int seq, int chars, String preview) {}

    public record ChunkPreviewPageView(
            String url,
            String title,
            int markdownChars,
            int chunkCount,
            boolean chunksTruncated,
            java.util.List<ChunkPreviewChunkView> chunks,
            String error) {}

    public record ChunkPreviewView(
            int strategyCode,
            int fixedChars,
            int slideOverlap,
            java.util.List<ChunkPreviewPageView> pages,
            int pageCount,
            int totalChunkCount,
            int discoveredUrlCount) {}
}
