package com.aaron.cloud.scheduled;

import com.aaron.cloud.common.api.enums.TenantScheduledExecutorCode;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.scheduled.TenantScheduledTaskRepository;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.CreateScheduledTaskRequest;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledTaskAdminView;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledTaskMetaView;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.UpdateScheduledTaskRequest;
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
    private final TenantScheduledTaskExecutor taskExecutor;

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

    public void runNow(long id) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        TenantScheduledTask row =
                taskRepository
                        .findById(tenantId, id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found"));
        LocalDateTime now = BeijingTime.nowLocal();
        taskExecutor.dispatch(row);
        row.setLastExecAt(now);
        row.setNextExecAt(TenantScheduledCronSupport.computeNextExecAt(row, now));
        taskRepository.updateById(row);
    }

    private static void validateCron(String cron) {
        try {
            org.springframework.scheduling.support.CronExpression.parse(cron.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cron expression");
        }
    }

    private ScheduledTaskAdminView toView(TenantScheduledTask s) {
        return new ScheduledTaskAdminView(
                s.getId(),
                s.getExecutorCode() != null ? s.getExecutorCode().getCode() : null,
                s.getExecutorCode() != null ? s.getExecutorCode().getLabel() : null,
                s.getName(),
                s.getCronExpression(),
                s.getEnabled() != null && s.getEnabled() == 1,
                formatTime(s.getLastExecAt()),
                formatTime(s.getNextExecAt()),
                formatTime(s.getCreatedAt()),
                formatTime(s.getUpdatedAt()));
    }

    private static String formatTime(LocalDateTime t) {
        return t != null ? t.format(ISO) : null;
    }
}
