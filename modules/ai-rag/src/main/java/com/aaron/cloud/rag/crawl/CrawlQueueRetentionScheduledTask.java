package com.aaron.cloud.rag.crawl;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.aaron.cloud.common.rag.CrawlRunRepository;
import com.aaron.cloud.common.rag.CrawlUrlQueueRepository;
import com.aaron.cloud.common.rag.entity.CrawlRun;
import com.aaron.cloud.common.rag.mapper.CrawlRunMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 清理 30 天前的 crawl_run / crawl_url_queue 历史。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlQueueRetentionScheduledTask {

    private final CrawlRunMapper crawlRunMapper;
    private final CrawlRunRepository crawlRunRepository;
    private final CrawlUrlQueueRepository crawlUrlQueueRepository;
    private final PlatformSettingApplicationService platformSettings;

    @Scheduled(cron = "0 * * * * *")
    public void purgeOldQueuesIfDue() {
        if (!platformSettings.isCronDue(PlatformSettingKey.RAG_SITE_CRAWL_QUEUE_RETENTION_CRON)) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<CrawlRun> old =
                crawlRunMapper.selectList(
                        Wrappers.<CrawlRun>lambdaQuery().lt(CrawlRun::getCreatedAt, cutoff).select(CrawlRun::getId));
        if (old.isEmpty()) {
            return;
        }
        List<Long> ids = old.stream().map(CrawlRun::getId).toList();
        int q = crawlUrlQueueRepository.deleteByRunIds(ids);
        int r = crawlRunRepository.deleteOlderThan(cutoff);
        log.info("crawl queue retention deleted runs={} queueRows={}", r, q);
    }
}
