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
import java.util.ArrayList;
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
    public Page<MeteringUsageEvent> pageForAdmin(
            Long filterTenantIdOrNull, long pageNo, long pageSize, String usageSceneOrNull) {
        var q = Wrappers.<MeteringUsageEvent>lambdaQuery().orderByDesc(MeteringUsageEvent::getCreatedAt);
        if (filterTenantIdOrNull != null) {
            q.eq(MeteringUsageEvent::getTenantId, filterTenantIdOrNull);
        }
        if (usageSceneOrNull != null && !usageSceneOrNull.isBlank()) {
            q.apply(
                    "CAST(JSON_EXTRACT(ref_json, '$.usageScene') AS CHAR(64)) = {0}",
                    usageSceneOrNull.trim());
        }
        Page<MeteringUsageEvent> page = mapper.selectPage(Page.of(pageNo, pageSize), q);
        attachAdminDisplayFields(page.getRecords());
        return page;
    }

    /** 兼容旧调用（不按场景筛选）。 */
    public Page<MeteringUsageEvent> pageForAdmin(Long filterTenantIdOrNull, long pageNo, long pageSize) {
        return pageForAdmin(filterTenantIdOrNull, pageNo, pageSize, null);
    }

    /** 近 {@code days} 个北京自然日（含今日）按 {@code usageScene} 汇总 token。 */
    public List<MeteringUsageBySceneView> sumTokensGroupedByUsageScene(
            Long filterTenantIdOrNull, int days) {
        int d = Math.clamp(days, 1, 90);
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        java.time.LocalDate start = today.minusDays(d - 1L);
        java.time.LocalDateTime since =
                start.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime();
        java.time.LocalDateTime until =
                today.plusDays(1L).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime();
        QueryWrapper<MeteringUsageEvent> qw = new QueryWrapper<>();
        qw.select(
                        MeteringTokenAggregationSql.USAGE_SCENE_EXPR + " AS usage_scene",
                        "COALESCE(SUM(quantity),0) AS total_tokens",
                        "COUNT(1) AS event_count")
                .eq("unit", "token")
                .ge("created_at", since)
                .lt("created_at", until);
        if (filterTenantIdOrNull != null) {
            qw.eq("tenant_id", filterTenantIdOrNull);
        }
        qw.groupBy("usage_scene").orderByDesc("total_tokens");
        List<Map<String, Object>> maps = mapper.selectMaps(qw);
        List<MeteringUsageBySceneView> out = new ArrayList<>();
        for (Map<String, Object> row : maps) {
            String scene = row.get("usage_scene") == null ? "-" : String.valueOf(row.get("usage_scene"));
            long tokens = toLong(row.get("total_tokens"));
            long cnt = toLong(row.get("event_count"));
            if ("-".equals(scene) && tokens <= 0L && cnt <= 0L) {
                continue;
            }
            out.add(new MeteringUsageBySceneView(scene, tokens, cnt));
        }
        return out;
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return new BigDecimal(String.valueOf(v)).longValue();
        } catch (Exception ex) {
            return 0L;
        }
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

    /** 数据概览：近时段内 token 计量行（仅必要列，供应用层解析 {@code ref_json}）。 */
    public List<MeteringUsageEvent> listTokenEventsInRange(
            long tenantId, LocalDateTime sinceInclusive, LocalDateTime untilExclusive) {
        return mapper.selectList(
                Wrappers.<MeteringUsageEvent>lambdaQuery()
                        .select(
                                MeteringUsageEvent::getCreatedAt,
                                MeteringUsageEvent::getQuantity,
                                MeteringUsageEvent::getRefJson)
                        .eq(MeteringUsageEvent::getTenantId, tenantId)
                        .eq(MeteringUsageEvent::getUnit, "token")
                        .ge(MeteringUsageEvent::getCreatedAt, sinceInclusive)
                        .lt(MeteringUsageEvent::getCreatedAt, untilExclusive));
    }

    /** 近时段租户级输入/输出 Token（来自 {@code ref_json}，无拆分时将 {@code quantity} 计入输出）。 */
    public Map<String, Object> sumTokenSplitByTenantSince(long tenantId, LocalDateTime sinceUtcInclusive) {
        QueryWrapper<MeteringUsageEvent> qw = new QueryWrapper<>();
        qw.select(MeteringTokenAggregationSql.SUM_PROMPT, MeteringTokenAggregationSql.SUM_COMPLETION)
                .eq("tenant_id", tenantId)
                .eq("unit", "token")
                .ge("created_at", sinceUtcInclusive);
        return mapper.selectMaps(qw).stream().findFirst().orElse(Map.of());
    }

    public List<Map<String, Object>> sumTokenSplitByTenantGroupedByBeijingDate(
            long tenantId, LocalDateTime sinceInclusive, LocalDateTime untilExclusive) {
        QueryWrapper<MeteringUsageEvent> qw = new QueryWrapper<>();
        qw.select(
                        "DATE(created_at) AS bucket",
                        MeteringTokenAggregationSql.SUM_PROMPT,
                        MeteringTokenAggregationSql.SUM_COMPLETION)
                .eq("tenant_id", tenantId)
                .eq("unit", "token")
                .ge("created_at", sinceInclusive)
                .lt("created_at", untilExclusive)
                .groupBy("DATE(created_at)");
        return mapper.selectMaps(qw);
    }

    /** 按模型别名聚合 Token，按总量降序，取前 {@code limit} 条。 */
    public List<Map<String, Object>> topModelsTokenSplitByTenantSince(
            long tenantId, LocalDateTime sinceUtcInclusive, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        QueryWrapper<MeteringUsageEvent> qw = new QueryWrapper<>();
        qw.select(
                        MeteringTokenAggregationSql.MODEL_ALIAS_EXPR + " AS model_alias",
                        MeteringTokenAggregationSql.SUM_PROMPT,
                        MeteringTokenAggregationSql.SUM_COMPLETION)
                .eq("tenant_id", tenantId)
                .eq("unit", "token")
                .ge("created_at", sinceUtcInclusive)
                .groupBy("model_alias")
                .last("ORDER BY (prompt_sum + completion_sum) DESC LIMIT " + limit);
        return mapper.selectMaps(qw);
    }

    /** 按 {@code ref_json.conversationId} 汇总会话内全部 token 计量（含联网、编排 LLM 等）。 */
    public long sumTokenQuantityByConversationId(long tenantId, long conversationId) {
        if (conversationId <= 0L) {
            return 0L;
        }
        QueryWrapper<MeteringUsageEvent> qw = new QueryWrapper<>();
        qw.select("COALESCE(SUM(quantity),0) AS s")
                .eq("tenant_id", tenantId)
                .eq("unit", "token")
                .apply(
                        "CAST(JSON_EXTRACT(ref_json, '$.conversationId') AS SIGNED) = {0}",
                        conversationId);
        Map<String, Object> row = mapper.selectMaps(qw).stream().findFirst().orElse(null);
        if (row == null || row.get("s") == null) {
            return 0L;
        }
        Object s = row.get("s");
        if (s instanceof BigDecimal bd) {
            return bd.longValue();
        }
        if (s instanceof Number n) {
            return n.longValue();
        }
        try {
            return new BigDecimal(String.valueOf(s)).longValue();
        } catch (Exception ex) {
            return 0L;
        }
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
