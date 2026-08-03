package com.aaron.cloud.scheduled;

import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.scheduled.run.TenantScheduledRunContext;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 按 {@link TenantScheduledExecutorCode} 分发到对应 {@link TenantScheduledJobHandler}。 */
@Slf4j
@Component
public class TenantScheduledTaskExecutor {

    private final Map<TenantScheduledExecutorCode, TenantScheduledJobHandler> handlers;

    public TenantScheduledTaskExecutor(List<TenantScheduledJobHandler> handlerList) {
        this.handlers =
                handlerList.stream()
                        .collect(
                                Collectors.toMap(
                                        TenantScheduledJobHandler::executorCode,
                                        Function.identity(),
                                        (a, b) -> {
                                            throw new IllegalStateException(
                                                    "duplicate scheduled job handler: " + a.executorCode());
                                        }));
    }

    public void dispatch(TenantScheduledTask task, TenantScheduledRunContext runContext) throws Exception {
        TenantScheduledExecutorCode code = task.getExecutorCode();
        if (code == null) {
            log.warn("定时任务缺少 executorCode taskId={}", task.getId());
            return;
        }
        TenantScheduledJobHandler handler = handlers.get(code);
        if (handler == null) {
            log.warn("未注册的执行器 taskId={} executor={}", task.getId(), code);
            return;
        }
        handler.execute(task, runContext);
    }
}
