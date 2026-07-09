package com.aaron.cloud.job;

import com.aaron.cloud.common.api.dto.job.JobDispatchMessage;
import com.aaron.cloud.common.api.ports.JobPublisherPort;
import com.aaron.cloud.common.infra.AiInternalResourceNames;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "true")
public class JobPublisher implements JobPublisherPort {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void publish(JobDispatchMessage message) {
        rocketMQTemplate.syncSend(AiInternalResourceNames.RocketMq.JOB_TOPIC, message);
    }
}
