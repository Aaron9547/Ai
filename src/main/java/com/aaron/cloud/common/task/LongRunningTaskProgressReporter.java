package com.aaron.cloud.common.task;

import java.util.Map;

/** 上报长耗时任务进度。 */
public interface LongRunningTaskProgressReporter {

    default void report(String stage, String message, Integer percent, Integer current, Integer total) {
        report(stage, message, percent, current, total, null);
    }

    void report(
            String stage,
            String message,
            Integer percent,
            Integer current,
            Integer total,
            Map<String, Object> detail);
}
