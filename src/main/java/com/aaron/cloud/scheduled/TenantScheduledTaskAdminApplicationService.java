package com.aaron.cloud.scheduled;

import com.aaron.cloud.common.api.enums.ScheduledRunTrigger;
import com.aaron.cloud.common.api.enums.TenantScheduledExecutorCode;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.scheduled.TenantScheduledRunRepository;
import com.aaron.cloud.common.scheduled.TenantScheduledTaskRepository;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledRun;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.CreateScheduledTaskRequest;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledRunSummaryView;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledRunTriggerResult;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledRunView;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledTaskAdminView;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledTaskMetaView;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.UpdateScheduledTaskRequest;
import com.aaron.cloud.scheduled.run.TenantScheduledRunOrchestrator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TenantScheduledTaskAdminApplicationService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final TenantScheduledTaskRepository taskRepository;
    private final TenantScheduledRunRepository runRepository;
    private final TenantScheduledRunOrchestrator runOrchestrator;

    public ScheduledTaskMetaView meta() {
        return new ScheduledTaskMetaView(TenantScheduledExecutorCode.metaList());
    }

    public List<ScheduledTaskAdminView> list(String executorFilter) {
        long tenantId = TenantContextHolder.require().getTenantId();
        TenantScheduledExecutorCode code =
                executorFilter != null && !executorFilter.isBlank()
                        ? TenantScheduledExecutorCode.fromCode(executorFilter)
                        : null;
        return taskRepository.listByTenant(tenantId, code).stream().map(this::toView).toList();
    }

    public ScheduledTaskAdminView create(CreateScheduledTaskRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        TenantScheduledExecutorCode executor = TenantScheduledExecutorCode.fromCode(req.getExecutorCode());
        validateCron(req.getCronExpression());
        var row = new TenantScheduledTask();
        row.setTenantId(tenantId);
        row.setExecutorCode(executor);
        row.setTaskType(executor.getCode());
        row.setName(req.getName().trim());
        row.setCronExpression(req.getCronExpression().trim());
        row.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
        row.setNextExecAt(TenantScheduledCronSupport.computeNextExecAt(row, BeijingTime.nowLocal()));
        taskRepository.insert(row);
        return toView(taskRepository.findById(tenantId, row.getId()).orElseThrow());
    }

    public ScheduledTaskAdminView update(long id, UpdateScheduledTaskRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        TenantScheduledTask row =
                taskRepository
                        .findById(tenantId, id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found"));
        if (req.getName() != null && !req.getName().isBlank()) {
            row.setName(req.getName().trim());
        }
        if (req.getCronExpression() != null && !req.getCronExpression().isBlank()) {
            validateCron(req.getCronExpression());
            row.setCronExpression(req.getCronExpression().trim());
            row.setNextExecAt(TenantScheduledCronSupport.computeNextExecAt(row, BeijingTime.nowLocal()));
        }
        if (req.getEnabled() != null) {
            row.setEnabled(req.getEnabled() ? 1 : 0);
        }
        taskRepository.updateById(row);
        return toView(taskRepository.findById(tenantId, id).orElseThrow());
    }

    public void delete(long id) {
        long tenantId = TenantContextHolder.require().getTenantId();
        if (taskRepository.findById(tenantId, id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found");
        }
        taskRepository.delete(tenantId, id);
    }

    public ScheduledRunTriggerResult runNow(long id) {
        return runOrchestrator.trigger(id, ScheduledRunTrigger.MANUAL);
    }

    public ScheduledRunView getActiveRun(long registrationId) {
        return runOrchestrator.getActiveRunForRegistration(registrationId);
    }

    public ScheduledRunView getRun(long runId) {
        return runOrchestrator.getRun(runId);
    }

    private static void validateCron(String cron) {
        try {
            org.springframework.scheduling.support.CronExpression.parse(cron.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cron expression");
        }
    }

    private ScheduledTaskAdminView toView(TenantScheduledTask s) {
        long tenantId = s.getTenantId();
        String executorCode = resolveExecutorCode(s);
        String executorLabel = resolveExecutorLabel(s, executorCode);
        ScheduledRunSummaryView activeRun = null;
        if (tenantId != null && s.getId() != null) {
            activeRun =
                    runRepository
                            .findActiveByRegistration(tenantId, s.getId())
                            .map(this::toRunSummary)
                            .orElse(null);
        }
        return new ScheduledTaskAdminView(
                s.getId(),
                executorCode,
                executorLabel,
                s.getName(),
                s.getCronExpression(),
                s.getEnabled() != null && s.getEnabled() == 1,
                formatTime(s.getLastExecAt()),
                formatTime(s.getNextExecAt()),
                formatTime(s.getCreatedAt()),
                formatTime(s.getUpdatedAt()),
                activeRun);
    }

    private ScheduledRunSummaryView toRunSummary(TenantScheduledRun r) {
        return new ScheduledRunSummaryView(
                r.getId(),
                r.getStatus() == null ? null : r.getStatus().getStorage(),
                r.getProgressJson());
    }

    private static String resolveExecutorCode(TenantScheduledTask s) {
        if (s.getExecutorCode() != null) {
            return s.getExecutorCode().getCode();
        }
        if (s.getTaskType() != null && !s.getTaskType().isBlank()) {
            return s.getTaskType().trim();
        }
        return null;
    }

    private static String resolveExecutorLabel(TenantScheduledTask s, String executorCode) {
        if (s.getExecutorCode() != null) {
            return s.getExecutorCode().getLabel();
        }
        return TenantScheduledExecutorCode.labelOfCode(executorCode);
    }

    private static String formatTime(LocalDateTime t) {
        return t != null ? t.format(ISO) : null;
    }
}
