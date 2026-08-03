package com.aaron.cloud.job;

import com.aaron.cloud.common.api.enums.job.JobTaskType;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.jobmeta.JobTaskRepository;
import com.aaron.cloud.common.jobmeta.entity.JobTask;
import com.aaron.cloud.job.dto.JobTaskAdminDtos.JobTaskAdminView;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobTaskAdminApplicationService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final JobTaskRepository jobTaskRepository;

    public JobTaskAdminView get(long id) {
        long tenantId = com.aaron.cloud.common.context.TenantContextHolder.require().getTenantId();
        return jobTaskRepository
                .findById(id, tenantId)
                .map(this::toView)
                .orElseThrow(
                        () ->
                                new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.NOT_FOUND, "job not found"));
    }

    public Page<JobTaskAdminView> page(long page, long size, JobTaskType taskType, Long ragKbId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        Page<JobTask> src = jobTaskRepository.pageByTenant(tenantId, page, size, taskType, ragKbId);
        Page<JobTaskAdminView> out = new Page<>(src.getCurrent(), src.getSize(), src.getTotal());
        out.setRecords(src.getRecords().stream().map(this::toView).toList());
        return out;
    }

    private JobTaskAdminView toView(JobTask t) {
        var v = new JobTaskAdminView();
        v.setId(t.getId());
        v.setTaskType(t.getTaskType() != null ? t.getTaskType().name() : "");
        v.setStatus(t.getStatus() != null ? t.getStatus().name() : "");
        v.setPayloadJson(t.getPayloadJson());
        v.setResultJson(t.getResultJson());
        v.setCreatedAt(t.getCreatedAt() != null ? ISO.format(t.getCreatedAt()) : null);
        v.setUpdatedAt(t.getUpdatedAt() != null ? ISO.format(t.getUpdatedAt()) : null);
        return v;
    }
}
