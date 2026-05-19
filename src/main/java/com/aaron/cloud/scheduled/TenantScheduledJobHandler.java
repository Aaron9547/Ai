package com.aaron.cloud.scheduled;

import com.aaron.cloud.common.api.enums.TenantScheduledExecutorCode;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;

/** 定时任务执行器：由调度层在到点时调用，内部自行扫业务表/入队。 */
public interface TenantScheduledJobHandler {

    TenantScheduledExecutorCode executorCode();

    void execute(TenantScheduledTask registration) throws Exception;
}
