package com.aaron.cloud.job.rest.api;

import com.aaron.cloud.common.api.enums.job.JobTaskType;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.job.JobTaskAdminApplicationService;
import com.aaron.cloud.job.dto.JobTaskAdminDtos.JobTaskAdminView;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端异步任务列表（知识中心流水线与全租户任务共用表 {@code job_task}）。 */
@RestController
@RequiredArgsConstructor
public class AdminJobTaskRestController extends ApiV1ControllerBases.AdminJobTasks {

    private final JobTaskAdminApplicationService jobTaskAdminApplicationService;

    @GetMapping("/{id}")
    public JobTaskAdminView get(@PathVariable long id) {
        return jobTaskAdminApplicationService.get(id);
    }

    @GetMapping
    public Page<JobTaskAdminView> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) JobTaskType taskType,
            @RequestParam(required = false) Long ragKbId) {
        return jobTaskAdminApplicationService.page(page, size, taskType, ragKbId);
    }
}
