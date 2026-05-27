package com.aaron.cloud.notification.message;

import com.aaron.cloud.common.api.dto.message.MessageDispatchMessage;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${ai.rocketmq.message-topic:ai-message-dispatch}",
        consumerGroup = "${ai.rocketmq.message-consumer-group:ai-message-consumer}")
public class MessageDispatchListener implements RocketMQListener<MessageDispatchMessage> {

    private final MessageDispatchService dispatchService;

    @Override
    public void onMessage(MessageDispatchMessage message) {
        try {
            TenantContextHolder.set(
                    TenantSnapshot.builder()
                            .tenantId(message.getTenantId())
                            .build());
            dispatchWithRetry(message);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void dispatchWithRetry(MessageDispatchMessage message) {
        try {
            dispatchService.dispatch(message);
        } catch (MessageDispatchService.MessageDispatchRetryException retry) {
            MessageDispatchMessage next =
                    MessageDispatchMessage.builder()
                            .deliveryLogId(message.getDeliveryLogId())
                            .tenantId(message.getTenantId())
                            .sceneCode(message.getSceneCode())
                            .recipient(message.getRecipient())
                            .templateVars(message.getTemplateVars())
                            .idempotencyKey(message.getIdempotencyKey())
                            .attemptCount(retry.getNextAttempt())
                            .build();
            dispatchWithRetry(next);
        }
    }
}
