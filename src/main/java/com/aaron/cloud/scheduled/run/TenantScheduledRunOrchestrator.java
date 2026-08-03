package com.aaron.cloud.scheduled.run;

import com.aaron.cloud.common.api.enums.scheduled.ScheduledRunStatus;
import com.aaron.cloud.common.api.enums.scheduled.ScheduledRunTrigger;
import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.redis.RedisDistributedLockService;
import com.aaron.cloud.common.scheduled.TenantScheduledRunRepository;
import com.aaron.cloud.common.scheduled.TenantScheduledTaskRepository;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledRun;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.common.task.LongRunningTaskProgress;
import com.aaron.cloud.common.task.LongRunningTaskProgressSupport;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.scheduled.TenantScheduledCronSupport;
import com.aaron.cloud.scheduled.TenantScheduledTaskExecutor;
import com.aaron.cloud.scheduled.TenantScheduledTaskLockKeys;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledRunTriggerResult;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledRunView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 定时任务异步执行编排：幂等（同注册项仅一条活跃 run）、进度落库、立即返回。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantScheduledRunOrchestrator {

    private static final Executor RUN_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final TenantScheduledTaskRepository taskRepository;
    private final TenantScheduledRunRepository runRepository;
    private final TenantScheduledTaskExecutor taskExecutor;
    private final RedisDistributedLockService distributedLockService;
    private final PlatformSettingApplicationService platformSettings;
    private final ObjectMapper objectMapper;

    public ScheduledRunTriggerResult trigger(long registrationId, ScheduledRunTrigger trigger) {
        long tenantId = TenantContextHolder.require().getTenantId();
        TenantScheduledTask registration =
                taskRepository
                        .findById(tenantId, registrationId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found"));

        Optional<TenantScheduledRun> active =
                runRepository.findActiveByRegistration(tenantId, registrationId);
        if (active.isPresent()) {
            return ScheduledRunTriggerResult.existing(toView(active.get()));
        }

        var run = new TenantScheduledRun();
        run.setTenantId(tenantId);
        run.setRegistrationId(registrationId);
        run.setExecutorCode(registration.getExecutorCode());
        run.setStatus(ScheduledRunStatus.PENDING);
        run.setTriggerType(trigger);
        run.setProgressJson(
                LongRunningTaskProgressSupport.toJson(
                        objectMapper,
                        new LongRunningTaskProgress("QUEUED", "已入队，等待执行", 0, null, null)));
        runRepository.insert(run);

        long runId = run.getId();
        TenantSnapshot snap = TenantContextHolder.require();
        RUN_EXECUTOR.execute(
                () -> {
                    try {
                        TenantContextHolder.set(snap);
                        executeRun(runId);
                    } finally {
                        TenantContextHolder.clear();
                    }
                });

        return ScheduledRunTriggerResult.started(toView(runRepository.findById(tenantId, runId).orElse(run)));
    }

    public ScheduledRunView getRun(long runId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        return runRepository
                .findById(tenantId, runId)
                .map(this::toView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run not found"));
    }

    public ScheduledRunView getActiveRunForRegistration(long registrationId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        return runRepository
                .findActiveByRegistration(tenantId, registrationId)
                .map(this::toView)
                .orElse(null);
    }

    private void executeRun(long runId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        TenantScheduledRun run =
                runRepository
                        .findById(tenantId, runId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run not found"));
        TenantScheduledTask registration =
                taskRepository
                        .findById(tenantId, run.getRegistrationId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found"));

        String lockKey =
                TenantScheduledTaskLockKeys.registrationRun(tenantId, registration.getId());
        long ttlSec = platformSettings.getLong(PlatformSettingKey.RAG_SCHEDULED_TASKS_TASK_LOCK_TTL_SECONDS);
        Optional<RedisDistributedLockService.DistributedLockHandle> lock =
                distributedLockService.tryAcquire(lockKey, Duration.ofSeconds(ttlSec));
        if (lock.isEmpty()) {
            failRun(run, "其它实例正在执行该定时任务");
            return;
        }

        LocalDateTime now = BeijingTime.nowLocal();
        try (var ignored = lock.get()) {
            run.setStatus(ScheduledRunStatus.RUNNING);
            run.setStartedAt(now);
            run.setProgressJson(
                    LongRunningTaskProgressSupport.toJson(
                            objectMapper,
                            new LongRunningTaskProgress("RUNNING", "执行中", null, null, null)));
            runRepository.updateById(run);

            TenantScheduledRunContext ctx =
                    new DbTenantScheduledRunContext(runId, tenantId, runRepository, objectMapper);
            try {
                taskExecutor.dispatch(registration, ctx);
                run = runRepository.findById(tenantId, runId).orElse(run);
                run.setStatus(ScheduledRunStatus.SUCCEEDED);
                run.setFinishedAt(BeijingTime.nowLocal());
                run.setProgressJson(
                        LongRunningTaskProgressSupport.toJson(
                                objectMapper,
                                new LongRunningTaskProgress("DONE", "执行完成", 100, null, null)));
                runRepository.updateById(run);

                registration.setLastExecAt(now);
                registration.setNextExecAt(TenantScheduledCronSupport.computeNextExecAt(registration, now));
                taskRepository.updateById(registration);
            } catch (Exception e) {
                log.error(
                        "定时任务 run 失败 runId={} registrationId={} executor={}",
                        runId,
                        registration.getId(),
                        registration.getExecutorCode(),
                        e);
                failRun(
                        runRepository.findById(tenantId, runId).orElse(run),
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
    }

    private void failRun(TenantScheduledRun run, String message) {
        run.setStatus(ScheduledRunStatus.FAILED);
        run.setFinishedAt(BeijingTime.nowLocal());
        if (message != null) {
            String m = message.trim();
            run.setErrorMessage(m.length() > 2000 ? m.substring(0, 2000) : m);
        }
        run.setProgressJson(
                LongRunningTaskProgressSupport.toJson(
                        objectMapper,
                        new LongRunningTaskProgress("FAILED", run.getErrorMessage(), null, null, null)));
        runRepository.updateById(run);
    }

    private ScheduledRunView toView(TenantScheduledRun r) {
        String executorCode =
                r.getExecutorCode() != null ? r.getExecutorCode().getCode() : null;
        String executorLabel =
                r.getExecutorCode() != null
                        ? r.getExecutorCode().getLabel()
                        : TenantScheduledExecutorCode.labelOfCode(
                                executorCode);
        return new ScheduledRunView(
                r.getId(),
                r.getRegistrationId(),
                executorCode,
                executorLabel,
                r.getStatus() == null ? null : r.getStatus().getStorage(),
                r.getTriggerType() == null ? null : r.getTriggerType().getCode(),
                r.getProgressJson(),
                r.getChildJobTaskIdsJson(),
                r.getErrorMessage(),
                formatTime(r.getStartedAt()),
                formatTime(r.getFinishedAt()),
                formatTime(r.getCreatedAt()),
                formatTime(r.getUpdatedAt()));
    }

    private static String formatTime(LocalDateTime t) {
        return t != null ? t.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
    }
}
