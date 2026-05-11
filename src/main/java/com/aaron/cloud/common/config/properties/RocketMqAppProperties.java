package com.aaron.cloud.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本项目中台自有 MQ 约定（{@code ai.rocketmq.*}）：开关、NameServer、生产者组、Job/用量 topic 与消费组等。
 * {@code name-server}、{@code producer-group} 由 {@link com.aaron.cloud.common.config.AiEnvironmentBridgePostProcessor} 写入
 * {@code rocketmq.name-server}、{@code rocketmq.producer.group}，与 RocketMQ starter 对齐。
 */
@Data
@ConfigurationProperties(prefix = "ai.rocketmq")
public class RocketMqAppProperties {

    /** 为 true 时导入 RocketMQ 自动配置并启用 Job/用量等监听器与发送端。 */
    private boolean enabled = false;

    /** NameServer；覆盖：ROCKETMQ_NAME_SRV；桥接到 {@code rocketmq.name-server} */
    private String nameServer = "";

    /** 生产者组；覆盖：ROCKETMQ_PRODUCER_GROUP；桥接到 {@code rocketmq.producer.group} */
    private String producerGroup = "ai-producer";

    private String jobTopic = "ai-job-dispatch";
    private String jobConsumerGroup = "ai-job-consumer";
    private String llmUsageTopic = "ai-llm-usage";
    private String llmUsageConsumerGroup = "ai-llm-usage-consumer";
}
