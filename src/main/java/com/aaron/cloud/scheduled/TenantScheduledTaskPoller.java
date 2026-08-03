package com.aaron.cloud.scheduled;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.api.enums.scheduled.ScheduledRunTrigger;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.aaron.cloud.common.redis.RedisDistributedLockService;
import com.aaron.cloud.common.scheduled.TenantScheduledTaskRepository;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.scheduled.run.TenantScheduledRunOrchestrator;
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
    private final TenantScheduledRunOrchestrator runOrchestrator;
    private final RedisDistributedLockService distributedLockService;
    private final PlatformSettingApplicationService platformSettings;

    @Scheduled(cron = "0 * * * * *")
    public void pollDueTasksIfDue() {
        if (!platformSettings.isCronDue(PlatformSettingKey.RAG_SCHEDULED_TASKS_POLL_CRON)) {
            return;
        }
        if (!platformSettings.getBoolean(PlatformSettingKey.RAG_SCHEDULED_TASKS_ENABLED)) {
            return;
        }
        long pollerTtlSec = platformSettings.getLong(PlatformSettingKey.RAG_SCHEDULED_TASKS_POLLER_LOCK_TTL_SECONDS);
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

    private void triggerRegistration(TenantScheduledTask task, LocalDateTime now) {
        try {
            var prev = com.aaron.cloud.common.context.TenantContextHolder.getOrNull();
            try {
                com.aaron.cloud.common.context.TenantContextHolder.set(
                        com.aaron.cloud.common.context.TenantSnapshot.builder()
                                .tenantId(task.getTenantId())
                                .build());
                var result = runOrchestrator.trigger(task.getId(), ScheduledRunTrigger.CRON);
                if (result.duplicate()) {
                    log.debug(
                            "定时任务 cron 跳过（已有执行中） taskId={} tenantId={} runId={}",
                            task.getId(),
                            task.getTenantId(),
                            result.runId());
                }
            } finally {
                if (prev != null) {
                    com.aaron.cloud.common.context.TenantContextHolder.set(prev);
                } else {
                    com.aaron.cloud.common.context.TenantContextHolder.clear();
                }
            }
        } catch (Exception e) {
            log.error(
                    "定时任务 cron 触发失败 taskId={} tenantId={}",
                    task.getId(),
                    task.getTenantId(),
                    e);
        }
    }
}
