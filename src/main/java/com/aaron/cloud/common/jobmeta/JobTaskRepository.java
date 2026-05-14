package com.aaron.cloud.common.jobmeta;

import com.aaron.cloud.common.api.enums.JobTaskStatus;
import com.aaron.cloud.common.jobmeta.entity.JobTask;
import com.aaron.cloud.common.jobmeta.mapper.JobTaskMapper;
import com.aaron.cloud.common.api.enums.JobTaskType;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JobTaskRepository {

    private final JobTaskMapper mapper;

    public Optional<JobTask> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<JobTask>lambdaQuery()
                                .eq(JobTask::getId, id)
                                .eq(JobTask::getTenantId, tenantId)));
    }

    public int insert(JobTask row) {
        return mapper.insert(row);
    }

    public int updateStatus(long id, long tenantId, JobTaskStatus status, String resultJson) {
        return mapper.update(
                Wrappers.<JobTask>lambdaUpdate()
                        .set(JobTask::getStatus, status)
                        .set(JobTask::getResultJson, resultJson)
                        .eq(JobTask::getId, id)
                        .eq(JobTask::getTenantId, tenantId));
    }

    /**
     * 管理端任务列表；{@code taskType} 非空时仅该类型。
     * {@code ragKbId} 非空时按 RAG 知识库筛选：优先 {@link com.aaron.cloud.common.jobmeta.entity.JobTask#getRagKbId()} 等值；列为空时回退
     * {@code payload_json} 中紧凑序列化的 {@code "kbId":} 片段（避免依赖部分环境缺失的 {@code JSON_EXTRACT}）。
     */
    public Page<JobTask> pageByTenant(
            long tenantId, long pageNo, long pageSize, JobTaskType taskType, Long ragKbId) {
        var q =
                Wrappers.<JobTask>lambdaQuery()
                        .eq(JobTask::getTenantId, tenantId)
                        .orderByDesc(JobTask::getId);
        if (taskType != null) {
            q.eq(JobTask::getTaskType, taskType);
        }
        if (ragKbId != null) {
            // 使用 CHAR 拼出 %、"kbId": 等，避免依赖 JSON 函数与反斜杠转义（兼容常见 sql_mode）。
            q.and(w ->
                    w.eq(JobTask::getRagKbId, ragKbId)
                            .or(x ->
                                    x.isNull(JobTask::getRagKbId)
                                            .apply(
                                                    "(payload_json LIKE CONCAT(CHAR(37), CHAR(34), 'kbId', CHAR(34), "
                                                            + "CHAR(58), CAST({0} AS CHAR), CHAR(44), CHAR(37)) "
                                                            + "OR payload_json LIKE CONCAT(CHAR(37), CHAR(34), 'kbId', CHAR(34), "
                                                            + "CHAR(58), CAST({0} AS CHAR), CHAR(125), CHAR(37)) "
                                                            + "OR payload_json LIKE CONCAT(CHAR(37), CHAR(34), 'kbId', CHAR(34), "
                                                            + "CHAR(58), CAST({0} AS CHAR), CHAR(93), CHAR(37)))",
                                                    ragKbId)));
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    public long countByTenantSince(long tenantId, LocalDateTime sinceUtcInclusive) {
        return mapper.selectCount(
                Wrappers.<JobTask>lambdaQuery()
                        .eq(JobTask::getTenantId, tenantId)
                        .ge(JobTask::getCreatedAt, sinceUtcInclusive));
    }

    public long countByTenantAndStatuses(long tenantId, List<JobTaskStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return 0;
        }
        return mapper.selectCount(
                Wrappers.<JobTask>lambdaQuery()
                        .eq(JobTask::getTenantId, tenantId)
                        .in(JobTask::getStatus, statuses));
    }
}
