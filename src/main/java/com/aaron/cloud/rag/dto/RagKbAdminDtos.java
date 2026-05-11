package com.aaron.cloud.rag.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

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
            String createdAt,
            String updatedAt) {}

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

    public record IndexJobResponse(long jobTaskId) {}

    @Data
    public static class UrlImportJobRequest {
        @NotBlank
        @Size(max = 2048)
        @URL
        private String url;

        /** 鍙€夛細鏈浠诲姟瑕嗙洊鍒嗙墖绛栫暐锛坽@link com.aaron.cloud.common.api.enums.RagChunkStrategy} 鐨?code锛夈€?*/
        private Integer chunkStrategy;
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
    }
}
