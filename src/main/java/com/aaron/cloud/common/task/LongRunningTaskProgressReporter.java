package com.aaron.cloud.common.task;

/** 上报长耗时任务进度。 */
@FunctionalInterface
public interface LongRunningTaskProgressReporter {

    void report(String stage, String message, Integer percent, Integer current, Integer total);
}
