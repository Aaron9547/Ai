package com.aaron.cloud.common.scheduled;

import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.common.scheduled.mapper.TenantScheduledTaskMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenantScheduledTaskRepository {

    private final TenantScheduledTaskMapper mapper;

    public List<TenantScheduledTask> listByTenant(long tenantId, TenantScheduledExecutorCode executorFilter) {
        var q =
                Wrappers.<TenantScheduledTask>lambdaQuery()
                        .eq(TenantScheduledTask::getTenantId, tenantId)
                        .orderByDesc(TenantScheduledTask::getUpdatedAt);
        if (executorFilter != null) {
            q.eq(TenantScheduledTask::getExecutorCode, executorFilter);
        }
        return mapper.selectList(q);
    }

    public List<TenantScheduledTask> listAllEnabled() {
        return mapper.selectList(
                Wrappers.<TenantScheduledTask>lambdaQuery().eq(TenantScheduledTask::getEnabled, 1));
    }

    public Optional<TenantScheduledTask> findById(long tenantId, long id) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenantScheduledTask>lambdaQuery()
                                .eq(TenantScheduledTask::getTenantId, tenantId)
                                .eq(TenantScheduledTask::getId, id)));
    }

    public int insert(TenantScheduledTask row) {
        return mapper.insert(row);
    }

    public int updateById(TenantScheduledTask row) {
        return mapper.updateById(row);
    }

    public int delete(long tenantId, long id) {
        return mapper.delete(
                Wrappers.<TenantScheduledTask>lambdaQuery()
                        .eq(TenantScheduledTask::getTenantId, tenantId)
                        .eq(TenantScheduledTask::getId, id));
    }
}
