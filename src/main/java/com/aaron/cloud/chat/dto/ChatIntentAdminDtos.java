package com.aaron.cloud.chat.dto;

import com.aaron.cloud.common.api.enums.ChatIntentHandlerKind;
import com.aaron.cloud.common.api.enums.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.ToggleState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class ChatIntentAdminDtos {

    private ChatIntentAdminDtos() {}

    public record IntentRow(
            long id,
            long tenantId,
            String code,
            String displayName,
            String description,
            ChatIntentHandlerKind handlerKind,
            ToggleState enabled,
            int sortOrder,
            String extraConfigJson) {}

    public record IntentCreateBody(
            @NotBlank String code,
            @NotBlank String displayName,
            String description,
            @NotNull ChatIntentHandlerKind handlerKind,
            @NotNull ToggleState enabled,
            Integer sortOrder,
            String extraConfigJson,
            Long targetTenantId) {}

    public record IntentUpdateBody(
            String displayName,
            String description,
            ChatIntentHandlerKind handlerKind,
            ToggleState enabled,
            Integer sortOrder,
            String extraConfigJson,
            Long targetTenantId) {}

    public record KeywordRow(
            long id,
            long intentId,
            String phrase,
            ChatIntentKeywordKind keywordKind,
            ToggleState enabled,
            int sortOrder) {}

    public record KeywordCreateBody(
            @NotBlank String phrase,
            @NotNull ChatIntentKeywordKind keywordKind,
            @NotNull ToggleState enabled,
            Integer sortOrder) {}

    public record KeywordUpdateBody(
            String phrase,
            ChatIntentKeywordKind keywordKind,
            ToggleState enabled,
            Integer sortOrder) {}
}
