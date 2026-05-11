package com.aaron.cloud.common.metering;

import com.aaron.cloud.common.metering.entity.MeteringUsageEvent;
import com.aaron.cloud.common.metering.mapper.MeteringUsageEventMapper;
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
public class MeteringUsageEventRepository {

    private final MeteringUsageEventMapper mapper;
    private final SysTenantRepository sysTenantRepository;
    private final SecUserAccountRepository secUserAccountRepository;

    public int insert(MeteringUsageEvent row) {
        return mapper.insert(row);
    }

    public Page<MeteringUsageEvent> pageByTenant(long tenantId, long pageNo, long pageSize) {
        Page<MeteringUsageEvent> page =
                mapper.selectPage(
                        Page.of(pageNo, pageSize),
                        Wrappers.<MeteringUsageEvent>lambdaQuery()
                                .eq(MeteringUsageEvent::getTenantId, tenantId)
                                .orderByDesc(MeteringUsageEvent::getCreatedAt));
        attachAdminDisplayFields(page.getRecords());
        return page;
    }

    /** {@code filterTenantId == null} 时不按租户过滤（创始人全量列表）。 */
    public Page<MeteringUsageEvent> pageForAdmin(Long filterTenantIdOrNull, long pageNo, long pageSize) {
        var q = Wrappers.<MeteringUsageEvent>lambdaQuery().orderByDesc(MeteringUsageEvent::getCreatedAt);
        if (filterTenantIdOrNull != null) {
            q.eq(MeteringUsageEvent::getTenantId, filterTenantIdOrNull);
        }
        Page<MeteringUsageEvent> page = mapper.selectPage(Page.of(pageNo, pageSize), q);
        attachAdminDisplayFields(page.getRecords());
        return page;
    }

    private void attachAdminDisplayFields(List<MeteringUsageEvent> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> tenantIds =
                records.stream()
                        .map(MeteringUsageEvent::getTenantId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        var tenants = sysTenantRepository.mapTenantsByIds(tenantIds);
        List<Long> userIds =
                records.stream().map(MeteringUsageEvent::getUserId).filter(Objects::nonNull).distinct().toList();
        var userLabels = secUserAccountRepository.mapUserDisplayLabelByIds(userIds);
        for (MeteringUsageEvent r : records) {
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
}
