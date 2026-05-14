package com.aaron.cloud.gateway.admin;

import java.util.List;

/** 管理端大屏 {@code GET /api/v1/admin/dashboard/summary} 响应体。 */
public record AdminDashboardSummaryView(
        long tenantId,
        /** 数据生成时间（北京时间，{@code yyyy-MM-dd HH:mm:ss}）。 */
        String generatedAt,
        Kpi kpi,
        Recent24h recent24h,
        List<DailyLong> httpAccessByDay,
        List<DailyDecimal> meteringQuantityByDay,
        List<DailyLong> meteringEventsByDay,
        List<NamedLong> memberLoginRegionCounts,
        List<LoginIpStat> topMemberClientIpsLast7d) {

    public record Kpi(
            long activeMemberCount,
            long conversationCount,
            long chatMessageCount,
            long llmModelTotal,
            long llmModelActive,
            long jobTaskPendingOrRunning,
            long jobTasksCreatedLast7d) {}

    public record Recent24h(
            long httpAccessCount, long meteringEventCount, double meteringQuantitySum, long auditEventCount) {}

    public record DailyLong(String day, long count) {}

    public record DailyDecimal(String day, double total) {}

    /** 在册成员按账号 {@code last_login_region} 聚合（空为「—」）。 */
    public record NamedLong(String name, long value) {}

    /** 近 7 日租户下已登录请求来源 IP 排行（按请求次数）。 */
    public record LoginIpStat(String clientIp, long distinctUsers, long hits) {}
}
