package com.aaron.cloud.common.accesslog;

import com.aaron.cloud.common.accesslog.entity.SysHttpAccessLog;
import com.aaron.cloud.common.accesslog.mapper.SysHttpAccessLogMapper;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SysHttpAccessLogRepository {

    private final SysHttpAccessLogMapper mapper;
    private final SysTenantRepository sysTenantRepository;
    private final SecUserAccountRepository secUserAccountRepository;

    public Page<SysHttpAccessLog> pageByTenant(long tenantId, long pageNo, long pageSize) {
        Page<SysHttpAccessLog> page =
                mapper.selectPage(
                        Page.of(pageNo, pageSize),
                        Wrappers.<SysHttpAccessLog>lambdaQuery()
                                .eq(SysHttpAccessLog::getTenantId, tenantId)
                                .orderByDesc(SysHttpAccessLog::getCreatedAt));
        attachDisplayFields(page.getRecords());
        return page;
    }

    /** {@code filterTenantId == null} 时不按租户过滤（创始人全量列表）。 */
    public Page<SysHttpAccessLog> pageForAdmin(Long filterTenantIdOrNull, long pageNo, long pageSize) {
        var q = Wrappers.<SysHttpAccessLog>lambdaQuery().orderByDesc(SysHttpAccessLog::getCreatedAt);
        if (filterTenantIdOrNull != null) {
            q.eq(SysHttpAccessLog::getTenantId, filterTenantIdOrNull);
        }
        Page<SysHttpAccessLog> page = mapper.selectPage(Page.of(pageNo, pageSize), q);
        attachDisplayFields(page.getRecords());
        return page;
    }

    private void attachDisplayFields(List<SysHttpAccessLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> tenantIds =
                records.stream().map(SysHttpAccessLog::getTenantId).filter(Objects::nonNull).distinct().toList();
        var tenants = sysTenantRepository.mapTenantsByIds(tenantIds);
        List<Long> userIds =
                records.stream().map(SysHttpAccessLog::getUserId).filter(Objects::nonNull).distinct().toList();
        var userLabels = secUserAccountRepository.mapUserDisplayLabelByIds(userIds);
        for (SysHttpAccessLog r : records) {
            if (r.getTenantId() != null) {
                var t = tenants.get(r.getTenantId());
                if (t != null) {
                    r.setTenantCode(t.getCode());
                    r.setTenantName(t.getName());
                }
            }
            if (r.getUserId() != null) {
                r.setUserDisplayName(userLabels.get(r.getUserId()));
            }
        }
    }

    public int insert(SysHttpAccessLog row) {
        return mapper.insert(row);
    }
}
