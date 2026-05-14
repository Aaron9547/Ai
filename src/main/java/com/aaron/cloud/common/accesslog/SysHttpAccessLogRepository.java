package com.aaron.cloud.common.accesslog;

import com.aaron.cloud.common.accesslog.entity.SysHttpAccessLog;
import com.aaron.cloud.common.accesslog.mapper.SysHttpAccessLogMapper;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * 在给定时间窗内，租户下出现过 HTTP 访问日志的用户 ID（去重）；用于管理端「在线」推断。
     */
    public Set<Long> findUserIdsWithRecentAccess(long tenantId, List<Long> userIds, LocalDateTime sinceUtcInclusive) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        List<Object> objs =
                mapper.selectObjs(
                        Wrappers.<SysHttpAccessLog>lambdaQuery()
                                .select(SysHttpAccessLog::getUserId)
                                .eq(SysHttpAccessLog::getTenantId, tenantId)
                                .in(SysHttpAccessLog::getUserId, userIds)
                                .ge(SysHttpAccessLog::getCreatedAt, sinceUtcInclusive)
                                .isNotNull(SysHttpAccessLog::getUserId));
        if (objs == null || objs.isEmpty()) {
            return Set.of();
        }
        HashSet<Long> out = new HashSet<>();
        for (Object o : objs) {
            if (o instanceof Number n) {
                out.add(n.longValue());
            }
        }
        return out;
    }

    public List<Map<String, Object>> topClientIpsByTenantSince(long tenantId, LocalDateTime sinceUtc, int limit) {
        return mapper.topClientIpsByTenantSince(tenantId, sinceUtc, limit);
    }

    public long countByTenantSince(long tenantId, LocalDateTime sinceUtcInclusive) {
        return mapper.selectCount(
                Wrappers.<SysHttpAccessLog>lambdaQuery()
                        .eq(SysHttpAccessLog::getTenantId, tenantId)
                        .ge(SysHttpAccessLog::getCreatedAt, sinceUtcInclusive));
    }

    /**
     * 按<strong>北京日历日</strong>聚合（{@code DATE(created_at)}）；用于管理端大屏。
     *
     * <p>要求 JDBC 会话为东八区（推荐 {@code connectionTimeZone=%2B08%3A00}），且 {@code created_at} 与 Java {@link LocalDateTime}
     * 均为东八区墙钟；区间参数同为墙钟（左闭右开）。
     *
     * @param untilExclusive 上界（不含）
     */
    public List<Map<String, Object>> countByTenantGroupedByBeijingDate(
            long tenantId, LocalDateTime sinceInclusive, LocalDateTime untilExclusive) {
        QueryWrapper<SysHttpAccessLog> qw = new QueryWrapper<>();
        qw.select("DATE(created_at) AS bucket", "COUNT(1) AS cnt")
                .eq("tenant_id", tenantId)
                .ge("created_at", sinceInclusive)
                .lt("created_at", untilExclusive)
                .groupBy("DATE(created_at)");
        return mapper.selectMaps(qw);
    }
}
