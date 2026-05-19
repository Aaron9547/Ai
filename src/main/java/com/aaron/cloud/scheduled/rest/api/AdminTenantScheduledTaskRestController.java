package com.aaron.cloud.scheduled.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.scheduled.TenantScheduledTaskAdminApplicationService;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.CreateScheduledTaskRequest;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledTaskAdminView;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.ScheduledTaskMetaView;
import com.aaron.cloud.scheduled.dto.TenantScheduledTaskAdminDtos.UpdateScheduledTaskRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminTenantScheduledTaskRestController extends ApiV1ControllerBases.AdminScheduledTasks {

    private final TenantScheduledTaskAdminApplicationService scheduledTaskAdminApplicationService;

    @GetMapping("/meta")
    public ScheduledTaskMetaView meta() {
        return scheduledTaskAdminApplicationService.meta();
    }

    @GetMapping
    public List<ScheduledTaskAdminView> list(@RequestParam(required = false) String executorCode) {
        return scheduledTaskAdminApplicationService.list(executorCode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledTaskAdminView create(@Valid @RequestBody CreateScheduledTaskRequest body) {
        return scheduledTaskAdminApplicationService.create(body);
    }

    @PutMapping("/{id}")
    public ScheduledTaskAdminView update(@PathVariable long id, @Valid @RequestBody UpdateScheduledTaskRequest body) {
        return scheduledTaskAdminApplicationService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        scheduledTaskAdminApplicationService.delete(id);
    }

    @PostMapping("/{id}/run")
    public Map<String, Boolean> runNow(@PathVariable long id) throws Exception {
        scheduledTaskAdminApplicationService.runNow(id);
        return Map.of("started", true);
    }
}
