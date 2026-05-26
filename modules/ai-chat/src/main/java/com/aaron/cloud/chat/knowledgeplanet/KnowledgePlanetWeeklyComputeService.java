package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.common.api.enums.profile.KnowledgeWeeklyInsightStatus;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetSubjectSupport;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.knowledgeplanet.TenUserKnowledgeNodeRepository;
import com.aaron.cloud.common.knowledgeplanet.TenUserWeeklyInsightRepository;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserWeeklyInsight;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.TenUserMemoryAbstractRepository;
import com.aaron.cloud.common.profile.TenUserMemoryChunkRepository;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.common.util.TextClamp;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyComputeService {

    private static final String WEEKLY_SYSTEM =
            """
            你是个人成长教练。根据用户过去一周的对话知识节点与记忆摘要，生成本周成长方案。
            只输出严格 JSON（不要 markdown）：
            {"summary":"一句话总览","thinkDirections":["方向1"],"gapAreas":["不足1"],"bookRecommendations":[{"title":"书名","reason":"理由"}]}
            thinkDirections 3～5 条；gapAreas 2～4 条；bookRecommendations 2～4 本。
            """;

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final TenUserKnowledgeNodeRepository nodeRepository;
    private final TenUserMemoryChunkRepository memoryChunkRepository;
    private final TenUserMemoryAbstractRepository memoryAbstractRepository;
    private final TenUserWeeklyInsightRepository insightRepository;
    private final KnowledgePlanetLlmSupport llmSupport;
    private final ObjectMapper objectMapper;

    public WeeklyComputeResult computeForTenant(long tenantId) {
        if (!planetRuntime.isEnabled(tenantId)) {
            return new WeeklyComputeResult(0, 0, 0, "租户未启用知识星球");
        }
        Optional<SysLlmModel> model = llmSupport.resolveLanguageModel(tenantId, null);
        if (model.isEmpty()) {
            return new WeeklyComputeResult(0, 0, 0, "未配置 LANGUAGE 模型");
        }

        LocalDate weekStart = mondayOfCurrentWeek();
        LocalDateTime since = weekStart.minusDays(7).atStartOfDay();
        Set<Long> userIds = new LinkedHashSet<>();
        for (String sk : nodeRepository.listDistinctUserSubjectKeysSince(tenantId, since)) {
            KnowledgePlanetSubjectSupport.parseUserId(sk).ifPresent(userIds::add);
        }

        int computed = 0;
        int skipped = 0;
        int failed = 0;
        for (Long userId : userIds) {
            try {
                boolean ok = computeOneUser(tenantId, userId, weekStart, model.get());
                if (ok) {
                    computed++;
                } else {
                    skipped++;
                }
            } catch (Exception ex) {
                failed++;
                log.warn("[知识星球] 周报计算失败 tenantId={} userId={}", tenantId, userId, ex);
                upsertFailed(tenantId, userId, weekStart, ex.getMessage());
            }
        }
        return new WeeklyComputeResult(computed, skipped, failed, null);
    }

    private boolean computeOneUser(long tenantId, long userId, LocalDate weekStart, SysLlmModel model)
            throws Exception {
        String subjectKey = ProfileSubjectKey.userKey(userId);
        List<TenUserKnowledgeNode> nodes = nodeRepository.listRecent(tenantId, subjectKey, 40);
        if (nodes.isEmpty()) {
            long chunks = memoryChunkRepository.countByTenantAndSubject(tenantId, subjectKey);
            if (chunks == 0) {
                return false;
            }
        }

        StringBuilder body = new StringBuilder();
        body.append("【本周知识节点】\n");
        for (TenUserKnowledgeNode n : nodes) {
            body.append("- ")
                    .append(n.getTitle())
                    .append("：")
                    .append(TextClamp.ellipsis(n.getSummary(), 200))
                    .append("\n");
        }
        memoryAbstractRepository
                .findByTenantAndSubject(tenantId, subjectKey)
                .ifPresent(
                        a ->
                                body.append("\n【记忆抽象】\n")
                                        .append(TextClamp.ellipsis(a.getBodyJson(), 3000)));

        String raw = llmSupport.invokeJson(tenantId, model, WEEKLY_SYSTEM, body.toString());
        KnowledgeWeeklyPlan plan = llmSupport.parseJson(raw, KnowledgeWeeklyPlan.class);
        if (plan == null) {
            return false;
        }

        var row =
                insightRepository
                        .findByUserWeek(tenantId, userId, weekStart)
                        .orElseGet(
                                () -> {
                                    var ins = new TenUserWeeklyInsight();
                                    ins.setTenantId(tenantId);
                                    ins.setUserId(userId);
                                    ins.setWeekStart(weekStart);
                                    return ins;
                                });
        row.setStatus(KnowledgeWeeklyInsightStatus.READY);
        row.setPlanJson(objectMapper.writeValueAsString(plan));
        row.setComputedAt(LocalDateTime.now());
        row.setErrorMessage(null);
        if (row.getId() == null) {
            insightRepository.insert(row);
        } else {
            insightRepository.updateById(row);
        }
        return true;
    }

    private void upsertFailed(long tenantId, long userId, LocalDate weekStart, String message) {
        var row =
                insightRepository
                        .findByUserWeek(tenantId, userId, weekStart)
                        .orElseGet(
                                () -> {
                                    var ins = new TenUserWeeklyInsight();
                                    ins.setTenantId(tenantId);
                                    ins.setUserId(userId);
                                    ins.setWeekStart(weekStart);
                                    return ins;
                                });
        row.setStatus(KnowledgeWeeklyInsightStatus.FAILED);
        row.setErrorMessage(TextClamp.ellipsis(message, 500));
        if (row.getId() == null) {
            insightRepository.insert(row);
        } else {
            insightRepository.updateById(row);
        }
    }

    public static LocalDate mondayOfCurrentWeek() {
        LocalDate today = BeijingTime.today();
        return today.with(DayOfWeek.MONDAY);
    }

    public record WeeklyComputeResult(int computed, int skipped, int failed, String skipReason) {}
}
