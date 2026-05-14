package com.aaron.cloud.common.metering;

import com.aaron.cloud.common.metering.entity.MeteringUsageEvent;
import com.aaron.cloud.common.metering.mapper.MeteringUsageEventMapper;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    public long countByTenantSince(long tenantId, LocalDateTime sinceUtcInclusive) {
        return mapper.selectCount(
                Wrappers.<MeteringUsageEvent>lambdaQuery()
                        .eq(MeteringUsageEvent::getTenantId, tenantId)
                        .ge(MeteringUsageEvent::getCreatedAt, sinceUtcInclusive));
    }

    /** {@code quantity} 按库 DECIMAL 汇总；无行时返回 {@link BigDecimal#ZERO}。 */
    public BigDecimal sumQuantityByTenantSince(long tenantId, LocalDateTime sinceUtcInclusive) {
        QueryWrapper<MeteringUsageEvent> qw = new QueryWrapper<>();
        qw.select("COALESCE(SUM(quantity),0) AS s")
                .eq("tenant_id", tenantId)
                .ge("created_at", sinceUtcInclusive);
        Map<String, Object> row = mapper.selectMaps(qw).stream().findFirst().orElse(null);
        if (row == null || row.get("s") == null) {
            return BigDecimal.ZERO;
        }
        Object s = row.get("s");
        if (s instanceof BigDecimal bd) {
            return bd;
        }
        if (s instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(s));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    public List<Map<String, Object>> sumQuantityByTenantGroupedByBeijingDate(
            long tenantId, LocalDateTime sinceInclusive, LocalDateTime untilExclusive) {
        QueryWrapper<MeteringUsageEvent> qw = new QueryWrapper<>();
        qw.select("DATE(created_at) AS bucket", "COALESCE(SUM(quantity),0) AS total")
                .eq("tenant_id", tenantId)
                .ge("created_at", sinceInclusive)
                .lt("created_at", untilExclusive)
                .groupBy("DATE(created_at)");
        return mapper.selectMaps(qw);
    }

    public List<Map<String, Object>> countByTenantGroupedByBeijingDate(
            long tenantId, LocalDateTime sinceInclusive, LocalDateTime untilExclusive) {
        QueryWrapper<MeteringUsageEvent> qw = new QueryWrapper<>();
        qw.select("DATE(created_at) AS bucket", "COUNT(1) AS cnt")
                .eq("tenant_id", tenantId)
                .ge("created_at", sinceInclusive)
                .lt("created_at", untilExclusive)
                .groupBy("DATE(created_at)");
        return mapper.selectMaps(qw);
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
