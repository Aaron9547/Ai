package com.aaron.cloud.common.config.properties;

import com.aaron.cloud.common.infra.AiInternalResourceNames;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 基础设施：仅保留开关、NameServer、生产者组；Topic/ConsumerGroup 见 {@link AiInternalResourceNames.RocketMq}。
 */
@Data
@ConfigurationProperties(prefix = "ai.rocketmq")
public class RocketMqAppProperties {

    private boolean enabled = false;
    private String nameServer = "";
    private String producerGroup = "ai-producer";

    public String getJobTopic() {
        return AiInternalResourceNames.RocketMq.JOB_TOPIC;
    }

    public String getJobConsumerGroup() {
        return AiInternalResourceNames.RocketMq.JOB_CONSUMER_GROUP;
    }

    public String getLlmUsageTopic() {
        return AiInternalResourceNames.RocketMq.LLM_USAGE_TOPIC;
    }

    public String getLlmUsageConsumerGroup() {
        return AiInternalResourceNames.RocketMq.LLM_USAGE_CONSUMER_GROUP;
    }

    public String getMemoryAbstractTopic() {
        return AiInternalResourceNames.RocketMq.MEMORY_ABSTRACT_TOPIC;
    }

    public String getMemoryAbstractConsumerGroup() {
        return AiInternalResourceNames.RocketMq.MEMORY_ABSTRACT_CONSUMER_GROUP;
    }

    public String getMessageTopic() {
        return AiInternalResourceNames.RocketMq.MESSAGE_TOPIC;
    }

    public String getMessageConsumerGroup() {
        return AiInternalResourceNames.RocketMq.MESSAGE_CONSUMER_GROUP;
    }
}
