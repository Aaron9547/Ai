package com.aaron.cloud.scheduled;

import com.aaron.cloud.rag.RagWebCrawlUrlSupport;

/** 租户定时任务 Redis 分布式锁 key。 */
public final class TenantScheduledTaskLockKeys {

    public static final String POLLER_TICK = "ai:lock:scheduled-task:poller:tick";

    private TenantScheduledTaskLockKeys() {}

    public static String registrationRun(long tenantId, long registrationId) {
        return "ai:lock:scheduled-task:run:" + tenantId + ":" + registrationId;
    }

    /** 单条爬站配置入队锁（rag_web_crawl_site.id）。 */
    public static String siteRun(long tenantId, long siteId) {
        return "ai:lock:rag-web-crawl-site:run:" + tenantId + ":" + siteId;
    }

    /** 一次性站点爬取（无定时任务 id）执行锁。 */
    public static String siteOneShot(long tenantId, String baseUrl, Long categoryId) {
        String norm = RagWebCrawlUrlSupport.normalizeUrl(baseUrl);
        return "ai:lock:scheduled-task:site:"
                + tenantId
                + ":"
                + norm
                + ":"
                + (categoryId == null ? "" : categoryId);
    }
}
