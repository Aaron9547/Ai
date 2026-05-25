package com.aaron.cloud.common.api.enums.rag;

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
