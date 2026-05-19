package com.aaron.cloud.scheduled.handler;

import com.aaron.cloud.common.api.enums.TenantScheduledExecutorCode;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.rag.RagWebCrawlSiteRepository;
import com.aaron.cloud.common.rag.entity.RagWebCrawlSite;
import com.aaron.cloud.common.redis.RedisDistributedLockService;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.rag.RagApplicationService;
import com.aaron.cloud.rag.RagWebCrawlSiteDueSupport;
import com.aaron.cloud.rag.RagWebCrawlSiteSupport;
import com.aaron.cloud.scheduled.TenantScheduledJobHandler;
import com.aaron.cloud.scheduled.TenantScheduledTaskLockKeys;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识库网页爬取调度执行器：扫描 {@code rag_web_crawl_site}，到期站点入队（对齐 ly-ai WebCrawlJobHandler.handle2）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagWebCrawlDispatchJobHandler implements TenantScheduledJobHandler {

    private final RagWebCrawlSiteRepository siteRepository;
    private final RagApplicationService ragApplicationService;
    private final RedisDistributedLockService distributedLockService;
    private final AiRagProperties aiRagProperties;

    @Override
    public TenantScheduledExecutorCode executorCode() {
        return TenantScheduledExecutorCode.RAG_WEB_CRAWL_DISPATCH;
    }

    @Override
    public void execute(TenantScheduledTask registration) throws Exception {
        long tenantId = registration.getTenantId();
        List<RagWebCrawlSite> sites = siteRepository.listAllEnabledForTenant(tenantId);
        if (sites.isEmpty()) {
            log.debug("网页爬取调度无启用站点 tenantId={}", tenantId);
            return;
        }
        LocalDateTime now = BeijingTime.nowLocal();
        int triggered = 0;
        int skipped = 0;
        for (RagWebCrawlSite site : sites) {
            if (!RagWebCrawlSiteDueSupport.isDue(site, now)) {
                skipped++;
                continue;
            }
            if (triggerSite(site)) {
                triggered++;
            }
        }
        log.info(
                "网页爬取调度完成 tenantId={} registrationId={} total={} triggered={} skipped={}",
                tenantId,
                registration.getId(),
                sites.size(),
                triggered,
                skipped);
    }

    private boolean triggerSite(RagWebCrawlSite site) throws Exception {
        String lockKey = TenantScheduledTaskLockKeys.siteRun(site.getTenantId(), site.getId());
        long ttlSec = aiRagProperties.getScheduledTasks().getTaskLockTtlSeconds();
        Optional<RedisDistributedLockService.DistributedLockHandle> lock =
                distributedLockService.tryAcquire(lockKey, Duration.ofSeconds(ttlSec));
        if (lock.isEmpty()) {
            log.debug("爬站跳过（持锁） siteId={}", site.getId());
            return false;
        }
        try (var ignored = lock.get()) {
            var mode = RagWebCrawlSiteSupport.resolveEffectiveSyncMode(site);
            ragApplicationService.enqueueSiteCrawlJobForSite(
                    site.getTenantId(),
                    site.getKbId(),
                    site.getBaseUrl(),
                    mode,
                    site.getMaxDepth(),
                    site.getFilterCrawled() != null && site.getFilterCrawled() == 1,
                    site.getChunkStrategy(),
                    site.getCategoryId(),
                    site.getId());
            site.setLastCrawlAt(BeijingTime.nowLocal());
            siteRepository.updateById(site);
            return true;
        }
    }
}
