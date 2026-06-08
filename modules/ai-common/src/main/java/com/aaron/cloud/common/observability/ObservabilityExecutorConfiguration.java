package com.aaron.cloud.common.observability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties({ObservabilityProperties.class, com.aaron.cloud.common.rag.RagQualityAssessmentProperties.class})
public class ObservabilityExecutorConfiguration {

    @Bean(name = "observabilityExecutor")
    public ThreadPoolTaskExecutor observabilityExecutor(ObservabilityProperties properties) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(Math.max(64, properties.getQueueCapacity()));
        ex.setThreadNamePrefix("obs-sink-");
        ex.setWaitForTasksToCompleteOnShutdown(false);
        ex.initialize();
        return ex;
    }

    @Bean(name = "ragQualityExecutor")
    public ThreadPoolTaskExecutor ragQualityExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(32);
        ex.setThreadNamePrefix("rag-qa-");
        ex.setWaitForTasksToCompleteOnShutdown(false);
        ex.initialize();
        return ex;
    }
}
