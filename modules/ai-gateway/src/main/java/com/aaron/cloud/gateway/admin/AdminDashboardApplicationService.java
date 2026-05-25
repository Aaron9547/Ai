package com.aaron.cloud.gateway.admin;

import com.aaron.cloud.common.accesslog.SysHttpAccessLogRepository;
import com.aaron.cloud.common.api.enums.job.JobTaskStatus;
import com.aaron.cloud.common.audit.SysAuditEventRepository;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.jobmeta.JobTaskRepository;
import com.aaron.cloud.common.metering.MeteringUsageEventRepository;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.time.BeijingTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理端「数据概览」大屏：基于当前租户已有落库表做只读聚合（HTTP 访问、计量、对话、任务、模型、成员、审计）。
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardApplicationService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final SysHttpAccessLogRepository accessLogRepository;
    private final MeteringUsageEventRepository meteringUsageEventRepository;
    private final MeteringTokenDashboardAggregator meteringTokenDashboardAggregator;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final JobTaskRepository jobTaskRepository;
    private final SysLlmModelRepository sysLlmModelRepository;
    private final SysTenantMemberRepository sysTenantMemberRepository;
    private final SysAuditEventRepository sysAuditEventRepository;
    private final SecUserAccountRepository secUserAccountRepository;

    public AdminDashboardSummaryView buildForTenant(long tenantId) {
        LocalDate todayBj = BeijingTime.today();
        LocalDate start7 = todayBj.minusDays(6);
        LocalDate start30 = todayBj.minusDays(MeteringTokenDashboardAggregator.TREND_DAYS - 1);
        LocalDateTime rangeStart = start7.atStartOfDay();
        LocalDateTime rangeEndExclusive = todayBj.plusDays(1).atStartOfDay();
        LocalDateTime since24h = BeijingTime.nowLocal().minusHours(24);
        LocalDateTime since7d = BeijingTime.nowLocal().minusDays(7);

        long members = sysTenantMemberRepository.countActiveByTenant(tenantId);
        long conv = chatConversationRepository.countByTenant(tenantId);
        long msgs = chatMessageRepository.countByTenant(tenantId);
        long llmTotal = sysLlmModelRepository.countByTenant(tenantId);
        long llmActive = sysLlmModelRepository.countActiveByTenant(tenantId);
        long jobBusy =
                jobTaskRepository.countByTenantAndStatuses(
                        tenantId, List.of(JobTaskStatus.PENDING, JobTaskStatus.RUNNING));
        long job7d = jobTaskRepository.countByTenantSince(tenantId, rangeStart);

        long http24 = accessLogRepository.countByTenantSince(tenantId, since24h);
        long met24 = meteringUsageEventRepository.countByTenantSince(tenantId, since24h);
        MeteringTokenDashboardAggregator.Aggregated meteringTokens =
                meteringTokenDashboardAggregator.aggregate(
                        tenantId, since24h, start7, start30, todayBj, rangeEndExclusive);
        long audit24 = sysAuditEventRepository.countByTenantSince(tenantId, since24h);

        List<Map<String, Object>> rawHttp =
                accessLogRepository.countByTenantGroupedByBeijingDate(tenantId, rangeStart, rangeEndExclusive);
        List<Map<String, Object>> rawMetCnt =
                meteringUsageEventRepository.countByTenantGroupedByBeijingDate(
                        tenantId, rangeStart, rangeEndExclusive);

        List<Map<String, Object>> rawRegions = secUserAccountRepository.countActiveMembersByLastLoginRegion(tenantId);
        List<Map<String, Object>> rawIpTop = accessLogRepository.topClientIpsByTenantSince(tenantId, since7d, 20);

        return new AdminDashboardSummaryView(
                tenantId,
                BeijingTime.nowDisplayString(),
                new AdminDashboardSummaryView.Kpi(
                        members, conv, msgs, llmTotal, llmActive, jobBusy, job7d),
                new AdminDashboardSummaryView.Recent24h(
                        http24, met24, meteringTokens.prompt24(), meteringTokens.completion24(), audit24),
                fillDailyLong(start7, todayBj, rawHttp, "cnt"),
                fillDailyTokenTotals(start7, todayBj, meteringTokens.dailyTokenRows7d()),
                fillDailyLong(start7, todayBj, rawMetCnt, "cnt"),
                new AdminDashboardSummaryView.TokenTotals(
                        longVal(meteringTokens.tenant7dRaw().get("prompt_sum")),
                        longVal(meteringTokens.tenant7dRaw().get("completion_sum"))),
                toModelDailyTrend(start30, todayBj, meteringTokens.modelTrendRaw30d()),
                toNamedLongs(rawRegions),
                toLoginIpStats(rawIpTop));
    }

    private static List<AdminDashboardSummaryView.NamedLong> toNamedLongs(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<AdminDashboardSummaryView.NamedLong> out = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            String name = m.get("bucket") == null ? "" : String.valueOf(m.get("bucket")).trim();
            Object c = m.get("cnt");
            long n = 0;
            if (c instanceof Number num) {
                n = num.longValue();
            } else if (c != null) {
                try {
                    n = Long.parseLong(String.valueOf(c).trim());
                } catch (NumberFormatException ignored) {
                    n = 0;
                }
            }
            out.add(new AdminDashboardSummaryView.NamedLong(name.isEmpty() ? "—" : name, n));
        }
        return out;
    }

    private static List<AdminDashboardSummaryView.LoginIpStat> toLoginIpStats(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<AdminDashboardSummaryView.LoginIpStat> out = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            String ip = m.get("clientIp") == null ? "" : String.valueOf(m.get("clientIp")).trim();
            long du = longVal(m.get("distinctUsers"));
            long hits = longVal(m.get("hits"));
            out.add(new AdminDashboardSummaryView.LoginIpStat(ip, du, hits));
        }
        return out;
    }

    private static long longVal(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<AdminDashboardSummaryView.DailyLong> fillDailyLong(
            LocalDate startInclusive,
            LocalDate endInclusive,
            List<Map<String, Object>> rows,
            String countKey) {
        Map<String, Long> byDay = new HashMap<>();
        for (Map<String, Object> m : rows) {
            String d = normalizeDayKey(m.get("bucket"));
            if (d.isEmpty()) {
                continue;
            }
            Object c = m.get(countKey);
            long n = 0;
            if (c instanceof Number num) {
                n = num.longValue();
            } else if (c != null) {
                try {
                    n = Long.parseLong(String.valueOf(c).trim());
                } catch (NumberFormatException ignored) {
                    n = 0;
                }
            }
            byDay.merge(d, n, Long::sum);
        }
        List<AdminDashboardSummaryView.DailyLong> out = new ArrayList<>();
        for (LocalDate d = startInclusive; !d.isAfter(endInclusive); d = d.plusDays(1)) {
            String key = d.format(DAY_FMT);
            out.add(new AdminDashboardSummaryView.DailyLong(key, byDay.getOrDefault(key, 0L)));
        }
        return out;
    }

    private static List<AdminDashboardSummaryView.DailyTokenTotals> fillDailyTokenTotals(
            LocalDate startInclusive, LocalDate endInclusive, List<Map<String, Object>> rows) {
        Map<String, long[]> byDay = new HashMap<>();
        for (Map<String, Object> m : rows) {
            String d = normalizeDayKey(m.get("bucket"));
            if (d.isEmpty()) {
                continue;
            }
            long p = longVal(m.get("prompt_sum"));
            long c = longVal(m.get("completion_sum"));
            byDay.merge(d, new long[] {p, c}, (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]});
        }
        List<AdminDashboardSummaryView.DailyTokenTotals> out = new ArrayList<>();
        for (LocalDate d = startInclusive; !d.isAfter(endInclusive); d = d.plusDays(1)) {
            String key = d.format(DAY_FMT);
            long[] v = byDay.getOrDefault(key, new long[] {0, 0});
            out.add(new AdminDashboardSummaryView.DailyTokenTotals(key, v[0], v[1]));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<AdminDashboardSummaryView.ModelDailyTokenSeries> toModelDailyTrend(
            LocalDate startInclusive, LocalDate endInclusive, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<AdminDashboardSummaryView.ModelDailyTokenSeries> out = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            String alias = m.get("model_alias") == null ? "—" : String.valueOf(m.get("model_alias")).trim();
            if (alias.isEmpty() || "-".equals(alias)) {
                alias = "—";
            }
            Object dailyObj = m.get("daily");
            List<Map<String, Object>> dailyRows =
                    dailyObj instanceof List<?> list
                            ? (List<Map<String, Object>>) list
                            : List.of();
            List<AdminDashboardSummaryView.DailyTokenTotals> daily =
                    fillDailyTokenTotals(startInclusive, endInclusive, dailyRows);
            out.add(new AdminDashboardSummaryView.ModelDailyTokenSeries(alias, daily));
        }
        return out;
    }

    private static String normalizeDayKey(Object bucket) {
        if (bucket == null) {
            return "";
        }
        if (bucket instanceof LocalDate ld) {
            return ld.format(DAY_FMT);
        }
        if (bucket instanceof java.sql.Date sd) {
            return sd.toLocalDate().format(DAY_FMT);
        }
        String s = String.valueOf(bucket).trim();
        if (s.length() >= 10) {
            return s.substring(0, 10);
        }
        return s;
    }
}
