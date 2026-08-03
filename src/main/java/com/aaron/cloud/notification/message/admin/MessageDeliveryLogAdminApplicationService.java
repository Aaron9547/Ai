package com.aaron.cloud.notification.message.admin;

import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.message.MsgDeliveryLogRepository;
import com.aaron.cloud.common.message.entity.MsgDeliveryLog;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.notification.dto.MessageAdminDtos.DeliveryLogPage;
import com.aaron.cloud.notification.dto.MessageAdminDtos.DeliveryLogRow;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageDeliveryLogAdminApplicationService {

    private final MsgDeliveryLogRepository deliveryLogRepository;
    private final ObjectMapper objectMapper;

    public DeliveryLogPage page(
            long pageNo,
            long pageSize,
            String sceneCode,
            String statusCode,
            String recipient,
            LocalDateTime from,
            LocalDateTime to) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        MessageSceneCode scene = parseScene(sceneCode);
        MessageDeliveryStatus status = parseStatus(statusCode);
        Page<MsgDeliveryLog> page =
                deliveryLogRepository.pageByTenant(
                        tid, pageNo, pageSize, scene, status, recipient, from, to);
        return new DeliveryLogPage(
                page.getTotal(), page.getRecords().stream().map(this::toRow).toList());
    }

    public DeliveryLogRow get(long id) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        MsgDeliveryLog row =
                deliveryLogRepository
                        .findById(id, tid)
                        .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "记录不存在"));
        return toRow(row);
    }

    private DeliveryLogRow toRow(MsgDeliveryLog row) {
        MessageDeliveryRequestJsonSupport.Parsed parsed =
                MessageDeliveryRequestJsonSupport.parse(objectMapper, row.getRequestJson());
        return new DeliveryLogRow(
                row.getId(),
                row.getSceneCode(),
                row.getChannelId(),
                row.getChannelType(),
                row.getRecipient(),
                row.getRequestJson(),
                parsed.messageSubject(),
                parsed.messageBody(),
                parsed.templateVars(),
                row.getProviderMsgId(),
                MessageAdminEnumSupport.deliveryStatusLabel(row.getStatus()),
                row.getErrorCode(),
                row.getErrorMessage(),
                row.getAttemptCount() == null ? 0 : row.getAttemptCount(),
                row.getIdempotencyKey(),
                row.getCreatedAt(),
                row.getFinishedAt());
    }

    private static MessageSceneCode parseScene(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return MessageSceneCode.fromCode(raw);
    }

    private static MessageDeliveryStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "QUEUED" -> MessageDeliveryStatus.QUEUED;
            case "SENDING" -> MessageDeliveryStatus.SENDING;
            case "SUCCEEDED" -> MessageDeliveryStatus.SUCCEEDED;
            case "FAILED" -> MessageDeliveryStatus.FAILED;
            default -> null;
        };
    }
}
