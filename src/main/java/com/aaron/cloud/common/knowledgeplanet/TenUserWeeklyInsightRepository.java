package com.aaron.cloud.common.knowledgeplanet;

import com.aaron.cloud.common.api.enums.profile.KnowledgeWeeklyInsightStatus;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserWeeklyInsight;
import com.aaron.cloud.common.knowledgeplanet.mapper.TenUserWeeklyInsightMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenUserWeeklyInsightRepository {

    private final TenUserWeeklyInsightMapper mapper;

    public Optional<TenUserWeeklyInsight> findByUserWeek(long tenantId, long userId, LocalDate weekStart) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenUserWeeklyInsight>lambdaQuery()
                                .eq(TenUserWeeklyInsight::getTenantId, tenantId)
                                .eq(TenUserWeeklyInsight::getUserId, userId)
                                .eq(TenUserWeeklyInsight::getWeekStart, weekStart)));
    }

    public int insert(TenUserWeeklyInsight row) {
        return mapper.insert(row);
    }

    public int updateById(TenUserWeeklyInsight row) {
        return mapper.updateById(row);
    }

    public List<TenUserWeeklyInsight> listReadyForWeek(long tenantId, LocalDate weekStart) {
        return mapper.selectList(
                Wrappers.<TenUserWeeklyInsight>lambdaQuery()
                        .eq(TenUserWeeklyInsight::getTenantId, tenantId)
                        .eq(TenUserWeeklyInsight::getWeekStart, weekStart)
                        .eq(TenUserWeeklyInsight::getStatus, KnowledgeWeeklyInsightStatus.READY));
    }

    public Optional<TenUserWeeklyInsight> findLatestForUser(long tenantId, long userId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenUserWeeklyInsight>lambdaQuery()
                                .eq(TenUserWeeklyInsight::getTenantId, tenantId)
                                .eq(TenUserWeeklyInsight::getUserId, userId)
                                .in(
                                        TenUserWeeklyInsight::getStatus,
                                        KnowledgeWeeklyInsightStatus.READY,
                                        KnowledgeWeeklyInsightStatus.SENT)
                                .orderByDesc(TenUserWeeklyInsight::getWeekStart)
                                .last("LIMIT 1")));
    }

    /** 近 N 周已计算周报（含 progress ledger），不含当周。 */
    public List<TenUserWeeklyInsight> listRecentComputedBeforeWeek(
            long tenantId, long userId, LocalDate beforeWeekStart, int limit) {
        return mapper.selectList(
                Wrappers.<TenUserWeeklyInsight>lambdaQuery()
                        .eq(TenUserWeeklyInsight::getTenantId, tenantId)
                        .eq(TenUserWeeklyInsight::getUserId, userId)
                        .lt(TenUserWeeklyInsight::getWeekStart, beforeWeekStart)
                        .in(
                                TenUserWeeklyInsight::getStatus,
                                KnowledgeWeeklyInsightStatus.READY,
                                KnowledgeWeeklyInsightStatus.SENT)
                        .orderByDesc(TenUserWeeklyInsight::getWeekStart)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 8))));
    }
}
