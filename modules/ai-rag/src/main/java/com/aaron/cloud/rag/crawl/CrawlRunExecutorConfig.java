package com.aaron.cloud.rag.crawl;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 站点爬取专用线程池（避免占用 ForkJoinPool.commonPool）。 */
@Configuration
public class CrawlRunExecutorConfig {

    public static final String CRAWL_RUN_EXECUTOR = "crawlRunExecutor";

    @Bean(name = CRAWL_RUN_EXECUTOR)
    Executor crawlRunExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setThreadNamePrefix("crawl-run-");
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(32);
        ex.setQueueCapacity(256);
        ex.setAllowCoreThreadTimeOut(true);
        ex.setKeepAliveSeconds(120);
        ex.initialize();
        return ex;
    }
}
