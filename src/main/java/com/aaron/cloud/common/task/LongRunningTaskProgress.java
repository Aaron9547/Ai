package com.aaron.cloud.common.task;

/** 长耗时任务进度（定时任务 run、job_task 等 JSON 共用）。 */
public record LongRunningTaskProgress(
        String stage,
        String message,
        Integer percent,
        Integer current,
        Integer total) {}
