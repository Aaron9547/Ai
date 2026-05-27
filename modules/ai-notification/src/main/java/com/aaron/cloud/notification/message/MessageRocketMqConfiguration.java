package com.aaron.cloud.notification.message;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "true")
@ImportAutoConfiguration(RocketMQAutoConfiguration.class)
public class MessageRocketMqConfiguration {}
