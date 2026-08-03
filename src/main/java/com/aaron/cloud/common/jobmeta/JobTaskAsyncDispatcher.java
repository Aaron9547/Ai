package com.aaron.cloud.common.jobmeta;

import com.aaron.cloud.common.api.dto.job.JobDispatchMessage;
import com.aaron.cloud.common.api.ports.JobPublisherPort;
import com.aaron.cloud.common.api.ports.JobTaskExecutionPort;
import com.aaron.cloud.common.jobmeta.entity.JobTask;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 异步入队后的任务派发：优先 RocketMQ；MQ 未启用时走进程内后台线程。
 *
 * <p>Producer（如 ai-rag 入队）与 Executor（ai-job {@link JobTaskExecutionPort}）经本类解耦，
 * 避免 Spring 构造期环依赖；接口定义在 ai-common，实现分属 ai-job / bootstrap。
 */
@Service
@RequiredArgsConstructor
public class JobTaskAsyncDispatcher {

    private final ObjectProvider<JobPublisherPort> jobPublisher;
    private final ObjectProvider<JobTaskExecutionPort> jobTaskExecutionPort;

    public void dispatch(JobTask task) {
        dispatch(task.getId(), task.getTenantId(), task.getDeviceId(), task.getUserId());
    }

    public void dispatch(long jobTaskId, long tenantId, String deviceId, Long userId) {
        JobPublisherPort pub = jobPublisher.getIfAvailable();
        if (pub != null) {
            pub.publish(
                    JobDispatchMessage.builder()
                            .traceId(MDC.get("traceId"))
                            .tenantId(tenantId)
                            .deviceId(deviceId)
                            .userId(userId)
                            .jobTaskId(jobTaskId)
                            .build());
            return;
        }
        JobTaskExecutionPort executor = jobTaskExecutionPort.getIfAvailable();
        if (executor != null) {
            CompletableFuture.runAsync(() -> executor.processTask(jobTaskId, tenantId));
        }
    }
}
