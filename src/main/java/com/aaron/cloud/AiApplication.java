package com.aaron.cloud;

import com.aaron.cloud.common.config.properties.AiCorsProperties;
import com.aaron.cloud.common.config.properties.AiMemoryProperties;
import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.config.properties.RocketMqAppProperties;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = "com.aaron.cloud",
        exclude = {RocketMQAutoConfiguration.class})
@EnableConfigurationProperties({
    AiProvidersProperties.class,
    AiCorsProperties.class,
    AiRagProperties.class,
    RocketMqAppProperties.class,
    AiMemoryProperties.class,
    AiOutboundResilienceProperties.class
})
@EnableScheduling
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
