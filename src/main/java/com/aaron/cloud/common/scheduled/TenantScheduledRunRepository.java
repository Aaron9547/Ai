package com.aaron.cloud.common.scheduled;

import com.aaron.cloud.common.api.enums.ScheduledRunStatus;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledRun;
import com.aaron.cloud.common.scheduled.mapper.TenantScheduledRunMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenantScheduledRunRepository {

    private final TenantScheduledRunMapper mapper;

    public Optional<TenantScheduledRun> findById(long tenantId, long runId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenantScheduledRun>lambdaQuery()
                                .eq(TenantScheduledRun::getTenantId, tenantId)
                                .eq(TenantScheduledRun::getId, runId)));
    }

    public Optional<TenantScheduledRun> findActiveByRegistration(long tenantId, long registrationId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenantScheduledRun>lambdaQuery()
                                .eq(TenantScheduledRun::getTenantId, tenantId)
                                .eq(TenantScheduledRun::getRegistrationId, registrationId)
                                .in(
                                        TenantScheduledRun::getStatus,
                                        List.of(ScheduledRunStatus.PENDING, ScheduledRunStatus.RUNNING))
                                .orderByDesc(TenantScheduledRun::getId)
                                .last("LIMIT 1")));
    }

    public int insert(TenantScheduledRun row) {
        return mapper.insert(row);
    }

    public int updateById(TenantScheduledRun row) {
        return mapper.updateById(row);
    }
}
