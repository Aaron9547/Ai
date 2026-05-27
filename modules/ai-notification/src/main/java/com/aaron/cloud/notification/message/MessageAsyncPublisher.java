package com.aaron.cloud.notification.message;

import com.aaron.cloud.common.api.dto.message.MessageDispatchMessage;
import com.aaron.cloud.common.config.properties.RocketMqAppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageAsyncPublisher {

    private final ObjectMapper objectMapper;
    private final MessageDispatchService dispatchService;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplate;
    private final RocketMqAppProperties rocketMqApp;

    @Value("${ai.message.sync-fallback-on-mq-failure:true}")
    private boolean syncFallbackOnMqFailure;

    public void publish(MessageDispatchMessage message) {
        try {
            if (rocketMqApp.isEnabled()) {
                RocketMQTemplate rocket = rocketMQTemplate.getIfAvailable();
                if (rocket != null) {
                    try {
                        rocket.syncSend(rocketMqApp.getMessageTopic(), message);
                        return;
                    } catch (Exception e) {
                        log.warn("rocket message dispatch send failed, fallback sync", e);
                        if (!syncFallbackOnMqFailure) {
                            throw e;
                        }
                    }
                }
            }
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
            dispatchService.dispatch(next);
        }
    }
}
