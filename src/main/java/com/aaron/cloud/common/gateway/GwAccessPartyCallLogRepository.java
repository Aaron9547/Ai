package com.aaron.cloud.common.gateway;

import com.aaron.cloud.common.gateway.entity.GwAccessPartyCallLog;
import com.aaron.cloud.common.gateway.mapper.GwAccessPartyCallLogMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GwAccessPartyCallLogRepository {

    private final GwAccessPartyCallLogMapper mapper;

    public int insert(GwAccessPartyCallLog row) {
        return mapper.insert(row);
    }

    public Page<GwAccessPartyCallLog> pageByTenant(
            long tenantId,
            Long accessPartyId,
            Long endpointId,
            Integer httpStatus,
            LocalDateTime since,
            long pageNo,
            long pageSize) {
        var q =
                Wrappers.<GwAccessPartyCallLog>lambdaQuery()
                        .eq(GwAccessPartyCallLog::getTenantId, tenantId)
                        .orderByDesc(GwAccessPartyCallLog::getCreatedAt);
        if (accessPartyId != null) {
            q.eq(GwAccessPartyCallLog::getAccessPartyId, accessPartyId);
        }
        if (endpointId != null) {
            q.eq(GwAccessPartyCallLog::getEndpointId, endpointId);
        }
        if (httpStatus != null) {
            q.eq(GwAccessPartyCallLog::getHttpStatus, httpStatus);
        }
        if (since != null) {
            q.ge(GwAccessPartyCallLog::getCreatedAt, since);
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    public GwAccessPartyCallLog findById(long id) {
        return mapper.selectById(id);
    }

    public long countSince(long tenantId, LocalDateTime since) {
        return mapper.selectCount(
                Wrappers.<GwAccessPartyCallLog>lambdaQuery()
                        .eq(GwAccessPartyCallLog::getTenantId, tenantId)
                        .ge(GwAccessPartyCallLog::getCreatedAt, since));
    }

    public long countSuccessSince(long tenantId, LocalDateTime since) {
        return mapper.selectCount(
                Wrappers.<GwAccessPartyCallLog>lambdaQuery()
                        .eq(GwAccessPartyCallLog::getTenantId, tenantId)
                        .ge(GwAccessPartyCallLog::getCreatedAt, since)
                        .ge(GwAccessPartyCallLog::getHttpStatus, 200)
                        .lt(GwAccessPartyCallLog::getHttpStatus, 300));
    }

    public List<GwAccessPartyCallLog> listRecent(long tenantId, int limit) {
        return mapper.selectList(
                Wrappers.<GwAccessPartyCallLog>lambdaQuery()
                        .eq(GwAccessPartyCallLog::getTenantId, tenantId)
                        .orderByDesc(GwAccessPartyCallLog::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }
}
