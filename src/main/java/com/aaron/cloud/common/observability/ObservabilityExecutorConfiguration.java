package com.aaron.cloud.common.observability;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ObservabilityExecutorConfiguration {

    @Bean(name = "observabilityExecutor")
    public ThreadPoolTaskExecutor observabilityExecutor(PlatformSettingApplicationService platformSettings) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(Math.max(64, platformSettings.getInt(PlatformSettingKey.OBSERVABILITY_QUEUE_CAPACITY)));
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
