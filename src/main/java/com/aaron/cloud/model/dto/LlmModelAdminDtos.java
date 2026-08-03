package com.aaron.cloud.model.dto;

import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public final class LlmModelAdminDtos {

    private LlmModelAdminDtos() {}

    @Data
    public static class CreateLlmModelRequest {
        @NotBlank private String alias;
        @NotBlank private String displayName;
        @NotBlank private String openaiBaseUrl;
        @NotBlank private String openaiModelId;
        /** 默认 {@link LlmModelKind#LANGUAGE}；非语言模型不出现在 C 端对话选择器。 */
        private LlmModelKind modelKind;
        /**
         * 与 {@code llm_model.integration_backend}：{@link LlmModelKind#VECTOR} 为嵌入路径策略码；{@link LlmModelKind#WEB_SEARCH} 为联网检索实现码；省略时向量默认
         * {@code OPENAI_COMPATIBLE}。
         */
        private String integrationBackend;
        /**
         * 非 {@link LlmModelKind#VECTOR} 时须非空；{@link LlmModelKind#VECTOR} 可与内网免鉴权嵌入服务留空（不落库密文）。
         */
        private String apiKey;

        private boolean allowAnonymous;
        @Min(0)
        @Max(10)
        private Integer maxAttachments;
        private boolean supportsThinking;
        private boolean enabled = true;
        private Integer sortOrder;
        /** 共用 token 上限，{@code null} 表示不限制 */
        private Long tokenQuotaTotal;
        /** 是否本地部署；默认 false；向量模型为 true 时走 Feign（base-url 直连或 Eureka + service-id） */
        private Boolean localDeploy;
        /** 仅 {@link LlmModelKind#LANGUAGE}：主备链下一跳别名（须已存在且为 LANGUAGE）。 */
        private String fallbackModelAlias;
    }

    @Data
    public static class UpdateLlmModelRequest {
        private String displayName;
        private String openaiBaseUrl;
        private String openaiModelId;
        private LlmModelKind modelKind;
        /** 按当前 {@code model_kind} 校验并更新 {@code integration_backend} */
        private String integrationBackend;
        /** 仅轮换密钥时传入；非空则写入新密文 */
        private String apiKey;
        /** true：清除已保存的 API Key（如向量模型改为免鉴权） */
        private Boolean clearApiKey;

        private Boolean allowAnonymous;
        @Min(0)
        @Max(10)
        private Integer maxAttachments;
        private Boolean supportsThinking;
        private Boolean enabled;
        private Integer sortOrder;
        /** true：改为不限制额度；不设则不修改 */
        private Boolean tokenQuotaUnlimited;
        /** 共用 token 上限（与 tokenQuotaUnlimited 互斥使用） */
        private Long tokenQuotaTotal;
        /** 是否本地部署；全类型可持久化；仅 {@link LlmModelKind#VECTOR} 且为 true 时嵌入经 Feign（路径变量为租户 code） */
        private Boolean localDeploy;
        /** 仅 LANGUAGE：主备别名；传空字符串可清空 */
        private String fallbackModelAlias;
    }

    public record LlmModelAdminView(
            long id,
            String alias,
            String displayName,
            String openaiBaseUrl,
            String openaiModelId,
            LlmModelKind modelKind,
            /**
             * {@code llm_model.integration_backend}：VECTOR 为嵌入策略码；WEB_SEARCH 为联网实现码；其他类型为库内默认值（多为
             * OPENAI_COMPATIBLE）。
             */
            String integrationBackend,
            boolean apiKeyConfigured,
            boolean allowAnonymous,
            int maxAttachments,
            boolean supportsThinking,
            boolean enabled,
            int sortOrder,
            Long tokenQuotaTotal,
            long tokensUsed,
            String fallbackModelAlias,
            boolean localDeploy) {}
}
