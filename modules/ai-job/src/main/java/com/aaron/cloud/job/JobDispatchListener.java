package com.aaron.cloud.job;

import com.aaron.cloud.common.infra.AiInternalResourceNames;
import com.aaron.cloud.common.api.dto.job.JobDispatchMessage;
import com.aaron.cloud.common.api.ports.JobTaskExecutionPort;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantSnapshot;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = AiInternalResourceNames.RocketMq.JOB_TOPIC,
        consumerGroup = AiInternalResourceNames.RocketMq.JOB_CONSUMER_GROUP)
public class JobDispatchListener implements RocketMQListener<JobDispatchMessage> {

    private final JobTaskExecutionPort jobTaskExecutionPort;

    @Override
    public void onMessage(JobDispatchMessage message) {
        try {
            TenantContextHolder.set(
                    TenantSnapshot.builder()
                            .tenantId(message.getTenantId())
                            .userId(message.getUserId())
                            .deviceId(message.getDeviceId())
                            .build());
            jobTaskExecutionPort.processTask(message.getJobTaskId(), message.getTenantId());
        } finally {
            TenantContextHolder.clear();
        }
    }
}
