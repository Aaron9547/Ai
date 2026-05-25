package com.aaron.cloud.common.api.ports;

/** 异步任务进程内执行（MQ 未启用时的降级路径）；实现位于 {@code job} 域。 */
public interface JobTaskExecutionPort {

    void processTask(long jobTaskId, long tenantId);
}
