package com.aaron.cloud.notification.message;

import com.aaron.cloud.common.api.dto.message.MessageDispatchMessage;
import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import com.aaron.cloud.common.message.MsgDeliveryLogRepository;
import com.aaron.cloud.common.message.entity.MsgDeliveryLog;
import com.aaron.cloud.notification.message.MessageSceneReadinessService.RenderedMessage;
import com.aaron.cloud.notification.message.MessageSceneReadinessService.ResolvedMessageRoute;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDispatchService {

    private static final int MAX_ATTEMPTS = 3;

    private final MessageSceneReadinessService readinessService;
    private final MessageChannelRouter channelRouter;
    private final MsgDeliveryLogRepository deliveryLogRepository;
    private final ObjectMapper objectMapper;

    public void dispatch(MessageDispatchMessage message) {
        MsgDeliveryLog logRow =
                deliveryLogRepository
                        .findById(message.getDeliveryLogId(), message.getTenantId())
                        .orElseThrow(() -> new IllegalStateException("delivery_log_not_found"));
        int attempt = message.getAttemptCount() <= 0 ? 1 : message.getAttemptCount();
        logRow.setStatus(MessageDeliveryStatus.SENDING);
        logRow.setAttemptCount(attempt);
        deliveryLogRepository.updateById(logRow, message.getTenantId());

        try {
            ResolvedMessageRoute route = readinessService.resolve(message.getTenantId(), message.getSceneCode());
            Map<String, String> vars =
                    message.getTemplateVars() == null ? Map.of() : message.getTemplateVars();
            RenderedMessage rendered = RenderedMessage.render(route.template(), route.channel(), vars);
            logRow.setRequestJson(toRequestJson(message, rendered));
            deliveryLogRepository.updateById(logRow, message.getTenantId());
            String providerId =
                    channelRouter.send(
                            route.channel(),
                            message.getRecipient(),
                            rendered.subject(),
                            rendered.body(),
                            vars);
            logRow.setChannelId(route.channel().getId());
            logRow.setChannelType(route.channel().getChannelType());
            logRow.setProviderMsgId(providerId == null ? "" : providerId);
            logRow.setStatus(MessageDeliveryStatus.SUCCEEDED);
            logRow.setErrorCode(null);
            logRow.setErrorMessage(null);
            logRow.setFinishedAt(LocalDateTime.now());
            deliveryLogRepository.updateById(logRow, message.getTenantId());
        } catch (Exception ex) {
            log.warn(
                    "message dispatch failed logId={} attempt={}",
                    message.getDeliveryLogId(),
                    attempt,
                    ex);
            if (attempt < MAX_ATTEMPTS) {
                throw new MessageDispatchRetryException(ex.getMessage(), attempt + 1);
            }
            logRow.setStatus(MessageDeliveryStatus.FAILED);
            logRow.setErrorCode("dispatch_failed");
            logRow.setErrorMessage(ex.getMessage() == null ? "send failed" : ex.getMessage());
            logRow.setFinishedAt(LocalDateTime.now());
            deliveryLogRepository.updateById(logRow, message.getTenantId());
        }
    }

    public Map<String, Object> buildRequestSummary(MessageDispatchMessage message) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("sceneCode", message.getSceneCode().getCode());
        summary.put("recipient", message.getRecipient());
        summary.put("templateVars", message.getTemplateVars());
        return summary;
    }

    public String toRequestJson(MessageDispatchMessage message) {
        return toRequestJson(message, null);
    }

    public String toRequestJson(MessageDispatchMessage message, RenderedMessage rendered) {
        try {
            Map<String, Object> summary = buildRequestSummary(message);
            if (rendered != null) {
                summary.put("subject", rendered.subject() == null ? "" : rendered.subject());
                summary.put("body", rendered.body() == null ? "" : rendered.body());
            }
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static class MessageDispatchRetryException extends RuntimeException {
        private final int nextAttempt;

        public MessageDispatchRetryException(String message, int nextAttempt) {
            super(message);
            this.nextAttempt = nextAttempt;
        }

        public int getNextAttempt() {
            return nextAttempt;
        }
    }
}
