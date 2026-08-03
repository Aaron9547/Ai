package com.aaron.cloud.identity.knowledgeplanet;

import com.aaron.cloud.chat.knowledgeplanet.KnowledgePlanetWeeklyComputeService;
import com.aaron.cloud.chat.knowledgeplanet.KnowledgePlanetWeeklyComputeService.ComputeUserResult;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import com.aaron.cloud.common.knowledgeplanet.TenUserWeeklyInsightRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 管理端：单用户知识星球周报测试推送（可选手动落库 + 发信）。 */
@Service
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyAdminTestApplicationService {

    private final KnowledgePlanetWeeklyComputeService computeService;
    private final KnowledgePlanetWeeklyEmailApplicationService emailService;
    private final TenUserWeeklyInsightRepository insightRepository;
    private final ObjectMapper objectMapper;

    public WeeklyTestPushResult trigger(
            long tenantId, long userId, boolean persist, boolean sendEmail, LocalDate weekStart) {
        LocalDate ws =
                weekStart != null ? weekStart : KnowledgePlanetWeeklyComputeService.mondayOfCurrentWeek();
        ComputeUserResult compute = computeService.computeForUser(tenantId, userId, ws, persist);

        KnowledgePlanetWeeklyEmailApplicationService.SingleWeeklyEmailResult email = null;
        if (sendEmail) {
            KnowledgeWeeklyPlan plan = compute.plan();
            if (plan == null) {
                plan = loadPlanFromDb(tenantId, userId, ws).orElse(null);
            }
            if (plan == null) {
                email =
                        new KnowledgePlanetWeeklyEmailApplicationService.SingleWeeklyEmailResult(
                                "SKIPPED", "无可用周报内容（请先成功计算，或勾选落库）", null);
            } else {
                email =
                        emailService.sendTestForUser(
                                tenantId, userId, ws, plan, persist);
            }
        }
        return new WeeklyTestPushResult(compute, email);
    }

    private java.util.Optional<KnowledgeWeeklyPlan> loadPlanFromDb(
            long tenantId, long userId, LocalDate weekStart) {
        return insightRepository
                .findByUserWeek(tenantId, userId, weekStart)
                .flatMap(
                        ins -> {
                            if (ins.getPlanJson() == null || ins.getPlanJson().isBlank()) {
                                return java.util.Optional.empty();
                            }
                            try {
                                return java.util.Optional.of(
                                        objectMapper.readValue(ins.getPlanJson(), KnowledgeWeeklyPlan.class));
                            } catch (Exception ex) {
                                return java.util.Optional.empty();
                            }
                        });
    }

    public record WeeklyTestPushResult(ComputeUserResult compute, KnowledgePlanetWeeklyEmailApplicationService.SingleWeeklyEmailResult email) {}

    public static String describeCompute(ComputeUserResult compute) {
        if (compute == null) {
            return "";
        }
        String base = compute.outcome().name();
        if (compute.message() != null && !compute.message().isBlank()) {
            base = base + ": " + compute.message();
        }
        if (compute.persisted()) {
            base = base + " (已落库)";
        }
        return base;
    }

    public static String describeEmail(KnowledgePlanetWeeklyEmailApplicationService.SingleWeeklyEmailResult email) {
        if (email == null) {
            return "未请求发信";
        }
        String base = email.status();
        if (email.message() != null && !email.message().isBlank()) {
            base = base + ": " + email.message();
        }
        if (email.recipient() != null && !email.recipient().isBlank()) {
            base = base + " → " + email.recipient();
        }
        return base;
    }
}
