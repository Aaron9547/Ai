package com.aaron.cloud.notification.message;

import com.aaron.cloud.common.api.dto.message.MessageDispatchMessage;
import com.aaron.cloud.common.api.dto.message.MessageSendRequest;
import com.aaron.cloud.common.api.dto.message.MessageSendResult;
import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import com.aaron.cloud.common.message.MsgDeliveryLogRepository;
import com.aaron.cloud.common.message.entity.MsgDeliveryLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageSendApplicationService {

    private final MsgDeliveryLogRepository deliveryLogRepository;
    private final MessageDispatchService dispatchService;
    private final MessageAsyncPublisher asyncPublisher;

    public MessageSendResult send(MessageSendRequest request) {
        if (request.getRecipient() == null || request.getRecipient().isBlank()) {
            throw new IllegalArgumentException("recipient required");
        }
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            var existing =
                    deliveryLogRepository.findByIdempotency(
                            request.getTenantId(), request.getIdempotencyKey());
            if (existing.isPresent()) {
                MsgDeliveryLog row = existing.get();
                return MessageSendResult.builder()
                        .deliveryLogId(row.getId())
                        .status(row.getStatus())
                        .providerMsgId(row.getProviderMsgId())
                        .errorMessage(row.getErrorMessage())
                        .build();
            }
        }

        MsgDeliveryLog logRow = new MsgDeliveryLog();
        logRow.setTenantId(request.getTenantId());
        logRow.setSceneCode(request.getSceneCode());
        logRow.setRecipient(request.getRecipient().trim());
        logRow.setStatus(MessageDeliveryStatus.QUEUED);
        logRow.setAttemptCount(0);
        logRow.setIdempotencyKey(
                request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()
                        ? null
                        : request.getIdempotencyKey().trim());
        MessageDispatchMessage dispatch =
                MessageDispatchMessage.builder()
                        .tenantId(request.getTenantId())
                        .sceneCode(request.getSceneCode())
                        .recipient(request.getRecipient().trim())
                        .templateVars(request.getTemplateVars())
                        .idempotencyKey(logRow.getIdempotencyKey())
                        .attemptCount(1)
                        .build();
        logRow.setRequestJson(dispatchService.toRequestJson(dispatch));
        deliveryLogRepository.insert(logRow);
        dispatch.setDeliveryLogId(logRow.getId());

        if (request.isAsync()) {
            asyncPublisher.publish(dispatch);
            return MessageSendResult.builder()
                    .deliveryLogId(logRow.getId())
                    .status(MessageDeliveryStatus.QUEUED)
                    .build();
        }

        try {
            dispatchService.dispatch(dispatch);
        } catch (MessageDispatchService.MessageDispatchRetryException retry) {
            dispatch.setAttemptCount(retry.getNextAttempt());
            dispatchService.dispatch(dispatch);
        }
        MsgDeliveryLog updated =
                deliveryLogRepository
                        .findById(logRow.getId(), request.getTenantId())
                        .orElse(logRow);
        return MessageSendResult.builder()
                .deliveryLogId(updated.getId())
                .status(updated.getStatus())
                .providerMsgId(updated.getProviderMsgId())
                .errorMessage(updated.getErrorMessage())
                .build();
    }
}
