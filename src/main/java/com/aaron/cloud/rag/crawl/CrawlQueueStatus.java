package com.aaron.cloud.rag.crawl;

public enum CrawlQueueStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED,
    SKIPPED_NOT_MODIFIED,
    SKIPPED_ROBOTS,
    /** 已爬过滤（filterCrawled）等 */
    SKIPPED_FILTERED
}
