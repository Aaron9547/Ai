package com.aaron.cloud.job;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "true")
public class JobPublisher implements JobPublisherPort {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${ai.rocketmq.job-topic:ai-job-dispatch}")
    private String jobTopic;

    public void publish(JobDispatchMessage message) {
        rocketMQTemplate.syncSend(jobTopic, message);
    }
}
