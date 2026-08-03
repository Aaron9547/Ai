package com.aaron.cloud.notification.dto;

import com.aaron.cloud.common.api.enums.message.MessageChannelType;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

public final class MessageAdminDtos {

    private MessageAdminDtos() {}

    public record ChannelRow(
            long id,
            String channelCode,
            MessageChannelType channelType,
            String name,
            String configJson,
            boolean secretConfigured,
            String status,
            LocalDateTime updatedAt) {}

    public record ChannelCreateBody(
            @NotBlank String channelCode,
            @NotNull MessageChannelType channelType,
            @NotBlank String name,
            @NotBlank String configJson,
            String secretJson,
            String status) {}

    public record ChannelUpdateBody(
            String name,
            String configJson,
            String secretJson,
            String status) {}

    public record ChannelTestBody(@NotBlank String recipient, Map<String, String> templateVars) {}

    public record TemplateRow(
            long id,
            MessageSceneCode sceneCode,
            long channelId,
            String subjectTemplate,
            String bodyTemplate,
            String locale,
            String status,
            LocalDateTime updatedAt) {}

    public record TemplateCreateBody(
            @NotNull MessageSceneCode sceneCode,
            long channelId,
            String subjectTemplate,
            @NotBlank String bodyTemplate,
            String locale,
            String status) {}

    public record TemplateUpdateBody(
            Long channelId,
            String subjectTemplate,
            String bodyTemplate,
            String locale,
            String status) {}

    public record DeliveryLogRow(
            long id,
            MessageSceneCode sceneCode,
            Long channelId,
            MessageChannelType channelType,
            String recipient,
            String requestJson,
            String messageSubject,
            String messageBody,
            Map<String, String> templateVars,
            String providerMsgId,
            String status,
            String errorCode,
            String errorMessage,
            int attemptCount,
            String idempotencyKey,
            LocalDateTime createdAt,
            LocalDateTime finishedAt) {}

    public record DeliveryLogPage(
            long total,
            java.util.List<DeliveryLogRow> records) {}
}
