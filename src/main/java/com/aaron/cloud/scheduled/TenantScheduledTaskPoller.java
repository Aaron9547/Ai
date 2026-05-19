package com.aaron.cloud.scheduled;

import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.redis.RedisDistributedLockService;
import com.aaron.cloud.common.scheduled.TenantScheduledTaskRepository;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.common.time.BeijingTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 统一调度 tick：扫描 {@code ten_scheduled_task}，按 cron 触发执行器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantScheduledTaskPoller {

    private final TenantScheduledTaskRepository taskRepository;
    private final TenantScheduledTaskExecutor taskExecutor;
    private final RedisDistributedLockService distributedLockService;
    private final AiRagProperties aiRagProperties;

    @Scheduled(cron = "${ai.rag.scheduled-tasks.poll-cron:0 * * * * *}")
    public void pollDueTasks() {
        if (!aiRagProperties.getScheduledTasks().isEnabled()) {
            return;
        }
        long pollerTtlSec = aiRagProperties.getScheduledTasks().getPollerLockTtlSeconds();
        Optional<RedisDistributedLockService.DistributedLockHandle> pollerLock =
                distributedLockService.tryAcquire(TenantScheduledTaskLockKeys.POLLER_TICK, Duration.ofSeconds(pollerTtlSec));
        if (pollerLock.isEmpty()) {
            log.debug("定时任务调度 tick 跳过：其它实例持有 poller 锁");
            return;
        }
        try (var ignored = pollerLock.get()) {
            List<TenantScheduledTask> all = taskRepository.listAllEnabled();
            LocalDateTime now = BeijingTime.nowLocal();
            for (TenantScheduledTask task : all) {
                try {
                    if (!TenantScheduledCronSupport.isDue(task, now)) {
                        continue;
                    }
                    triggerRegistration(task, now);
                } catch (Exception e) {
                    log.error(
                            "定时任务调度失败 taskId={} tenantId={} executor={}",
                            task.getId(),
                            task.getTenantId(),
                            task.getExecutorCode(),
                            e);
                }
            }
        }
    }

    private void triggerRegistration(TenantScheduledTask task, LocalDateTime now) throws Exception {
        String lockKey = TenantScheduledTaskLockKeys.registrationRun(task.getTenantId(), task.getId());
        long ttlSec = aiRagProperties.getScheduledTasks().getTaskLockTtlSeconds();
        Optional<RedisDistributedLockService.DistributedLockHandle> lock =
                distributedLockService.tryAcquire(lockKey, Duration.ofSeconds(ttlSec));
        if (lock.isEmpty()) {
            log.debug("定时任务注册项跳过（持锁中） taskId={}", task.getId());
            return;
        }
        try (var ignored = lock.get()) {
            taskExecutor.dispatch(task);
            task.setLastExecAt(now);
            task.setNextExecAt(TenantScheduledCronSupport.computeNextExecAt(task, now));
            taskRepository.updateById(task);
        }
    }
}
