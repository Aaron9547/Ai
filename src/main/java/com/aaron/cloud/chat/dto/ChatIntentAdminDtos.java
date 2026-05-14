package com.aaron.cloud.chat.dto;

import com.aaron.cloud.common.api.enums.ChatIntentHandlerKind;
import com.aaron.cloud.common.api.enums.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.ToggleState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ChatIntentAdminDtos {

    private ChatIntentAdminDtos() {}

    public record IntentRow(
            long id,
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
            String extraConfigJson) {}

    public record IntentUpdateBody(
            String displayName,
            String description,
            ChatIntentHandlerKind handlerKind,
            ToggleState enabled,
            Integer sortOrder,
            String extraConfigJson) {}

    public record KeywordRow(
            long id,
            long intentId,
            String phrase,
            ChatIntentKeywordKind keywordKind,
            String targetRound,
            ToggleState enabled,
            int sortOrder,
            long hitCount) {}

    public record KeywordCreateBody(
            @NotBlank @Size(max = 128, message = "触发短语最多 128 个字符") String phrase,
            @NotNull ChatIntentKeywordKind keywordKind,
            @Size(max = 32) String targetRound,
            @NotNull ToggleState enabled,
            Integer sortOrder) {}

    public record KeywordUpdateBody(
            @Size(max = 128, message = "触发短语最多 128 个字符") String phrase,
            ChatIntentKeywordKind keywordKind,
            @Size(max = 32) String targetRound,
            ToggleState enabled,
            Integer sortOrder) {}

    /** 管理端处理器下拉：与 {@link ChatIntentHandlerKind} 及已注册插件对齐，勿在前端写死列表。 */
    public record IntentHandlerKindOption(String kind, String labelZh, String description) {}
}
