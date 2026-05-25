package com.aaron.cloud.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public final class ChatStarterPromptDtos {

    private ChatStarterPromptDtos() {}

    public record StarterPromptItem(Long id, String text, String source) {}

    public record StarterPromptListView(List<StarterPromptItem> items, boolean fallback) {}

    public record StarterEventBody(
            Long promptId, @NotBlank String scene, @NotBlank String eventType) {}

    public record PromptRow(
            long id,
            String scene,
            String source,
            String promptText,
            int weight,
            boolean enabled,
            Boolean requireThinking,
            Boolean requireWebSearch,
            LocalDate validFrom,
            LocalDate validUntil,
            int sortOrder,
            String batchKey,
            String createdAt,
            String updatedAt) {}

    public record PromptCreateBody(
            @NotBlank String scene,
            @NotBlank String promptText,
            Integer weight,
            Boolean enabled,
            Boolean requireThinking,
            Boolean requireWebSearch,
            LocalDate validFrom,
            LocalDate validUntil,
            Integer sortOrder) {}

    public record PromptUpdateBody(
            String promptText,
            Integer weight,
            Boolean enabled,
            Boolean requireThinking,
            Boolean requireWebSearch,
            LocalDate validFrom,
            LocalDate validUntil,
            Integer sortOrder) {}

    public record DailyBatchRow(
            long id,
            String topicDate,
            String status,
            List<String> questions,
            String errorMessage,
            String fetchedAt) {}

    public record RefreshDailyHotResult(boolean ok, String message, int questionCount) {}
}
