package com.aaron.cloud.scheduled.run;

/** 单次定时任务执行上下文：上报进度、登记子异步 job_task。 */
public interface TenantScheduledRunContext {

    long runId();

    void report(String stage, String message, Integer percent, Integer current, Integer total);

    void recordSpawnedJobTask(long jobTaskId);
}
