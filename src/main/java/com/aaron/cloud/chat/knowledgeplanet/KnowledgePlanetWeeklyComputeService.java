package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.common.api.enums.profile.KnowledgeWeeklyInsightStatus;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetSubjectSupport;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetWeeklyRecipientGate;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyProgressLedger;
import com.aaron.cloud.common.knowledgeplanet.TenUserKnowledgeNodeRepository;
import com.aaron.cloud.common.knowledgeplanet.TenUserWeeklyInsightRepository;
import com.aaron.cloud.common.knowledgeplanet.TenUserLearnerProfileBody;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserWeeklyInsight;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.TenUserMemoryChunkRepository;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.common.util.TextClamp;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyComputeService {

    private static final int RECENT_ALL_LIMIT = 40;

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final TenUserKnowledgeNodeRepository nodeRepository;
    private final TenUserMemoryChunkRepository memoryChunkRepository;
    private final TenUserWeeklyInsightRepository insightRepository;
    private final KnowledgePlanetLlmSupport llmSupport;
    private final ObjectMapper objectMapper;
    private final PromptTemplateResolvePort promptTemplates;
    private final KnowledgePlanetWeeklyContextBuilder contextBuilder;
    private final KnowledgePlanetWeeklyBookSearchSupport bookSearchSupport;
    private final KnowledgePlanetWeeklyBookEnrichSupport bookEnrichSupport;
    private final KnowledgePlanetLearnerProfileService learnerProfileService;
    private final KnowledgePlanetWeeklyRecipientGate recipientGate;

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
            if (!recipientGate.isEligible(tenantId, userId)) {
                skipped++;
                continue;
            }
            try {
                ComputeUserResult r = computeForUser(tenantId, userId, weekStart, true);
                if (r.outcome() == ComputeOutcome.COMPUTED) {
                    computed++;
                } else if (r.outcome() == ComputeOutcome.FAILED) {
                    failed++;
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

    /**
     * 单用户周报计算；{@code persist=false} 时不写入 {@code ten_user_weekly_insight} 与学习者画像增量。
     */
    public ComputeUserResult computeForUser(
            long tenantId, long userId, LocalDate weekStart, boolean persist) {
        LocalDate ws = weekStart != null ? weekStart : mondayOfCurrentWeek();
        if (!planetRuntime.isEnabled(tenantId)) {
            return new ComputeUserResult(ComputeOutcome.SKIPPED, "租户未启用知识星球", ws, null, false);
        }
        if (!recipientGate.isEligible(tenantId, userId)) {
            return new ComputeUserResult(ComputeOutcome.SKIPPED, "用户未启用", ws, null, false);
        }
        Optional<SysLlmModel> model = llmSupport.resolveLanguageModel(tenantId, null);
        if (model.isEmpty()) {
            return new ComputeUserResult(ComputeOutcome.SKIPPED, "未配置 LANGUAGE 模型", ws, null, false);
        }
        try {
            return computeOneUser(tenantId, userId, ws, model.get(), persist);
        } catch (Exception ex) {
            log.warn("[知识星球] 周报计算失败 tenantId={} userId={}", tenantId, userId, ex);
            if (persist) {
                upsertFailed(tenantId, userId, ws, ex.getMessage());
            }
            String msg = ex.getMessage() == null ? "compute failed" : ex.getMessage();
            return new ComputeUserResult(ComputeOutcome.FAILED, msg, ws, null, false);
        }
    }

    private ComputeUserResult computeOneUser(
            long tenantId, long userId, LocalDate weekStart, SysLlmModel model, boolean persist)
            throws Exception {
        String subjectKey = ProfileSubjectKey.userKey(userId);
        LocalDateTime weekSince = weekStart.minusDays(7).atStartOfDay();
        LocalDateTime weekUntil = weekStart.atStartOfDay();
        LocalDateTime priorSince = weekStart.minusDays(14).atStartOfDay();

        List<TenUserKnowledgeNode> weekNodes =
                nodeRepository.listSince(tenantId, subjectKey, weekSince, weekUntil, 12);
        List<TenUserKnowledgeNode> priorWeekNodes =
                nodeRepository.listSince(tenantId, subjectKey, priorSince, weekSince, 12);
        List<TenUserKnowledgeNode> recentAll = nodeRepository.listRecent(tenantId, subjectKey, RECENT_ALL_LIMIT);

        TenUserLearnerProfileBody learnerProfile = learnerProfileService.readBody(tenantId, userId);
        List<KnowledgeWeeklyProgressLedger> priorLedgers = loadPriorLedgers(tenantId, userId, weekStart, 3);
        KnowledgeWeeklyProgressLedger lastLedger = priorLedgers.isEmpty() ? null : priorLedgers.getFirst();

        long chunks = memoryChunkRepository.countByTenantAndSubject(tenantId, subjectKey);
        if (!passesActivityGate(tenantId, weekNodes, learnerProfile) && weekNodes.isEmpty() && chunks == 0) {
            return new ComputeUserResult(
                    ComputeOutcome.SKIPPED, "活跃门槛未满足或无节点/记忆", weekStart, null, false);
        }

        TenantSnapshot snap = TenantSnapshot.builder().tenantId(tenantId).userId(userId).build();
        String bookTopic = contextBuilder.pickBookSearchTopic(weekNodes, learnerProfile, lastLedger);
        var bookSearch = bookSearchSupport.searchIfEnabled(snap, bookTopic);

        var built =
                contextBuilder.build(
                        snap,
                        subjectKey,
                        userId,
                        weekNodes,
                        priorWeekNodes,
                        recentAll,
                        learnerProfile,
                        priorLedgers,
                        lastLedger,
                        bookSearch);

        String raw =
                llmSupport.invokeJson(
                        tenantId,
                        model,
                        promptTemplates.resolveSystem("planet_weekly_system", tenantId, "zh-CN"),
                        built.userPayload());
        KnowledgeWeeklyPlan plan = llmSupport.parseJson(raw, KnowledgeWeeklyPlan.class);
        if (plan == null || !hasMeaningfulPlan(plan)) {
            return new ComputeUserResult(ComputeOutcome.SKIPPED, "模型未产出有效周报", weekStart, null, false);
        }

        if (bookSearch.query() != null && !bookSearch.query().isBlank() && plan.getBookSearchQuery().isBlank()) {
            plan.setBookSearchQuery(bookSearch.query());
        }
        bookEnrichSupport.enrichUrls(plan, bookSearch.references());

        if (!persist) {
            return new ComputeUserResult(ComputeOutcome.COMPUTED, null, weekStart, plan, false);
        }

        KnowledgeWeeklyProgressLedger ledger = learnerProfileService.buildLedger(weekStart, plan);
        learnerProfileService.mergeDelta(tenantId, userId, weekStart, plan.getLearnerProfileDelta());

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
        row.setProgressLedgerJson(objectMapper.writeValueAsString(ledger));
        row.setComputedAt(LocalDateTime.now());
        row.setErrorMessage(null);
        if (row.getId() == null) {
            insightRepository.insert(row);
        } else {
            insightRepository.updateById(row);
        }
        return new ComputeUserResult(ComputeOutcome.COMPUTED, null, weekStart, plan, true);
    }

    private boolean passesActivityGate(
            long tenantId, List<TenUserKnowledgeNode> weekNodes, TenUserLearnerProfileBody learnerProfile) {
        int min = planetRuntime.weeklyMinNodes(tenantId);
        if (weekNodes.size() >= min) {
            return true;
        }
        if (learnerProfile == null) {
            return false;
        }
        boolean hasProfile =
                (learnerProfile.getRoleOrStage() != null && !learnerProfile.getRoleOrStage().isBlank())
                        || (learnerProfile.getCoreInterests() != null
                                && !learnerProfile.getCoreInterests().isEmpty())
                        || (learnerProfile.getLearningGoals() != null
                                && !learnerProfile.getLearningGoals().isEmpty());
        return hasProfile && !weekNodes.isEmpty();
    }

    private List<KnowledgeWeeklyProgressLedger> loadPriorLedgers(
            long tenantId, long userId, LocalDate weekStart, int limit) {
        List<KnowledgeWeeklyProgressLedger> out = new ArrayList<>();
        for (TenUserWeeklyInsight ins :
                insightRepository.listRecentComputedBeforeWeek(tenantId, userId, weekStart, limit)) {
            KnowledgeWeeklyProgressLedger ledger = parseLedger(ins);
            if (ledger != null) {
                out.add(ledger);
            }
        }
        return out;
    }

    private KnowledgeWeeklyProgressLedger parseLedger(TenUserWeeklyInsight ins) {
        if (ins.getProgressLedgerJson() != null && !ins.getProgressLedgerJson().isBlank()) {
            try {
                return objectMapper.readValue(ins.getProgressLedgerJson(), KnowledgeWeeklyProgressLedger.class);
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (ins.getPlanJson() == null || ins.getPlanJson().isBlank()) {
            return null;
        }
        try {
            KnowledgeWeeklyPlan plan = objectMapper.readValue(ins.getPlanJson(), KnowledgeWeeklyPlan.class);
            return learnerProfileService.buildLedger(ins.getWeekStart(), plan);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean hasMeaningfulPlan(KnowledgeWeeklyPlan plan) {
        return plan.getSummary() != null && !plan.getSummary().isBlank();
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

    /**
     * 库表 {@code ten_user_weekly_insight.week_start} 为编排锚点（通常为「本周一」）；节点统计区间为
     * {@code [weekStart-7d, weekStart)}，即上一自然周。邮件/对外文案须展示该覆盖区间，而非锚点当天。
     */
    public static LocalDate coveredWeekMonday(LocalDate insightWeekStart) {
        return insightWeekStart.minusDays(7);
    }

    public static LocalDate coveredWeekSunday(LocalDate insightWeekStart) {
        return insightWeekStart.minusDays(1);
    }

    public static String formatCoveredWeekLabel(LocalDate insightWeekStart) {
        return coveredWeekMonday(insightWeekStart) + " 至 " + coveredWeekSunday(insightWeekStart);
    }

    public enum ComputeOutcome {
        COMPUTED,
        SKIPPED,
        FAILED
    }

    public record ComputeUserResult(
            ComputeOutcome outcome, String message, LocalDate weekStart, KnowledgeWeeklyPlan plan, boolean persisted) {}

    public record WeeklyComputeResult(int computed, int skipped, int failed, String skipReason) {}
}
