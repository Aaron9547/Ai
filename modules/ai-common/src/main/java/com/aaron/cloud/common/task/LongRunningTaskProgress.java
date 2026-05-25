package com.aaron.cloud.common.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** 长耗时任务进度（定时任务 run、job_task 等 JSON 共用）。 */
public record LongRunningTaskProgress(
        String stage,
        String message,
        Integer percent,
        Integer current,
        Integer total,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> detail) {

    public LongRunningTaskProgress(
            String stage, String message, Integer percent, Integer current, Integer total) {
        this(stage, message, percent, current, total, null);
    }
}
