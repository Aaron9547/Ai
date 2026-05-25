package com.aaron.cloud.chat.dto;

import com.aaron.cloud.common.api.enums.guardrail.GuardrailSensitivePoolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

public final class ChatSensitiveTermAdminDtos {

    private ChatSensitiveTermAdminDtos() {}

    /** 管理端敏感词分页行；{@code tenantCode} 为扩展池租户编码（与数据租户下拉框内一致），平台强制池为空。 */
    public record SensitiveTermRow(
            long id, GuardrailSensitivePoolType poolType, String tenantCode, String word, LocalDateTime createdAt) {}

    @Data
    public static class SensitiveTermAddBody {
        @NotNull private GuardrailSensitivePoolType pool;
        @NotBlank
        @Size(max = 190)
        private String word;
        /**
         * 仅 {@link GuardrailSensitivePoolType#TENANT} 且调用方为创始人时可选：指定写入哪个租户的扩展池；缺省为 JWT
         * 工作区租户。
         */
        private Long targetTenantId;
    }

    @Data
    public static class SensitiveTermImportBody {
        @NotNull private GuardrailSensitivePoolType pool;
        /** 多行文本，每行一条；逗号分隔亦支持 */
        @NotBlank
        @Size(max = 100_000)
        private String text;
        /** 语义同 {@link SensitiveTermAddBody#targetTenantId}。 */
        private Long targetTenantId;
    }

    public record SensitiveTermImportResult(int inserted, int skippedDuplicates, int skippedInvalid) {}
}
