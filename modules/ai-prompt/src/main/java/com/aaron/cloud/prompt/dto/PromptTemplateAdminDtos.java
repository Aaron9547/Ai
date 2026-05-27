package com.aaron.cloud.prompt.dto;

import com.aaron.cloud.common.api.enums.prompt.PromptTemplateDomain;
import com.aaron.cloud.common.api.enums.prompt.PromptTemplateKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public final class PromptTemplateAdminDtos {

    private PromptTemplateAdminDtos() {}

    public record Row(
            long id,
            long tenantId,
            boolean platformDefault,
            String promptCode,
            PromptTemplateKind promptKind,
            PromptTemplateDomain domain,
            String locale,
            String content,
            String variablesSchemaJson,
            int version,
            boolean enabled,
            String remark,
            int sortOrder,
            LocalDateTime updatedAt) {}

    public record CreateBody(
            @NotBlank String promptCode,
            @NotNull PromptTemplateKind promptKind,
            @NotNull PromptTemplateDomain domain,
            String locale,
            @NotBlank String content,
            String variablesSchemaJson,
            Integer version,
            Boolean enabled,
            String remark,
            Integer sortOrder) {}

    public record UpdateBody(
            String content,
            String variablesSchemaJson,
            Boolean enabled,
            String remark,
            Integer sortOrder) {}

    public record CacheEvictBody(Long tenantId, String promptCode, String locale) {}

    /** {@code redisEnabled=false} 时命中/未中/键数均为 0，且失效按钮无实际效果。 */
    public record CacheStatsView(boolean redisEnabled, long approximateKeyCount, long hits, long misses) {}
}
