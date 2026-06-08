package com.aaron.cloud.identity.knowledgeplanet;

import com.aaron.cloud.chat.knowledgeplanet.KnowledgePlanetWeeklyComputeService;
import com.aaron.cloud.common.api.dto.message.MessageSendRequest;
import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.api.enums.profile.KnowledgeWeeklyInsightStatus;
import com.aaron.cloud.common.api.ports.MessageSendPort;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetWeeklyRecipientGate;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import com.aaron.cloud.common.knowledgeplanet.TenUserWeeklyInsightRepository;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserWeeklyInsight;
import com.aaron.cloud.common.message.MessageSceneReadinessQuery;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyEmailApplicationService {

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final MessageSceneReadinessQuery sceneReadinessQuery;
    private final MessageSendPort messageSendPort;
    private final TenUserWeeklyInsightRepository insightRepository;
    private final SecUserAccountRepository userAccountRepository;
    private final KnowledgePlanetWeeklyRecipientGate recipientGate;
    private final SysTenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public WeeklyEmailResult sendForTenant(long tenantId, LocalDate weekStart) {
        if (!planetRuntime.isEnabled(tenantId)) {
            return new WeeklyEmailResult(0, 0, 0, "租户未启用知识星球");
        }
        if (!planetRuntime.isWeeklyEmailEnabled(tenantId)) {
            return new WeeklyEmailResult(0, 0, 0, "周报邮件未启用");
        }
        if (!sceneReadinessQuery.isSceneConfigured(tenantId, MessageSceneCode.KNOWLEDGE_PLANET_WEEKLY)) {
            return new WeeklyEmailResult(0, 0, 0, "邮件通道未就绪");
        }

        String tenantName =
                tenantRepository.findById(tenantId).map(t -> t.getName()).orElse("AI");
        List<TenUserWeeklyInsight> ready = insightRepository.listReadyForWeek(tenantId, weekStart);
        int sent = 0;
        int skipped = 0;
        int failed = 0;

        for (TenUserWeeklyInsight ins : ready) {
            try {
                var ineligible = recipientGate.skipReason(tenantId, ins.getUserId());
                if (ineligible.isPresent()) {
                    markSkipped(ins, ineligible.get());
                    skipped++;
                    continue;
                }
                SecUserAccount user =
                        userAccountRepository
                                .findById(ins.getUserId())
                                .orElse(null);
                if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                    markSkipped(ins, "用户未绑定邮箱");
                    skipped++;
                    continue;
                }
                KnowledgeWeeklyPlan plan = parsePlan(ins.getPlanJson());
                Map<String, String> vars = buildVars(tenantName, user, weekStart, plan);
                var result =
                        messageSendPort.send(
                                MessageSendRequest.builder()
                                        .tenantId(tenantId)
                                        .sceneCode(MessageSceneCode.KNOWLEDGE_PLANET_WEEKLY)
                                        .recipient(user.getEmail().trim())
                                        .templateVars(vars)
                                        .idempotencyKey(
                                                "kp-weekly:"
                                                        + tenantId
                                                        + ":"
                                                        + ins.getUserId()
                                                        + ":"
                                                        + weekStart)
                                        .async(true)
                                        .build());
                if (result.getStatus() == MessageDeliveryStatus.FAILED) {
                    throw new IllegalStateException(
                            result.getErrorMessage() == null ? "send failed" : result.getErrorMessage());
                }
                ins.setStatus(KnowledgeWeeklyInsightStatus.SENT);
                ins.setEmailedAt(LocalDateTime.now());
                ins.setErrorMessage(null);
                insightRepository.updateById(ins);
                sent++;
            } catch (Exception ex) {
                failed++;
                log.warn("[知识星球] 邮件发送失败 tenantId={} insightId={}", tenantId, ins.getId(), ex);
                ins.setStatus(KnowledgeWeeklyInsightStatus.FAILED);
                ins.setErrorMessage(ex.getMessage() == null ? "send failed" : ex.getMessage());
                insightRepository.updateById(ins);
            }
        }
        return new WeeklyEmailResult(sent, skipped, failed, null);
    }

    private void markSkipped(TenUserWeeklyInsight ins, String reason) {
        ins.setStatus(KnowledgeWeeklyInsightStatus.SKIPPED);
        ins.setErrorMessage(reason);
        insightRepository.updateById(ins);
    }

    private KnowledgeWeeklyPlan parsePlan(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return new KnowledgeWeeklyPlan();
        }
        return objectMapper.readValue(json, KnowledgeWeeklyPlan.class);
    }

    private Map<String, String> buildVars(
            String tenantName, SecUserAccount user, LocalDate weekStart, KnowledgeWeeklyPlan plan) {
        String userName =
                user.getDisplayName() != null && !user.getDisplayName().isBlank()
                        ? user.getDisplayName().trim()
                        : (user.getLoginName() != null ? user.getLoginName() : "用户");
        return Map.of(
                "tenantName", tenantName == null ? "" : tenantName,
                "userName", userName,
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "weekLabel", KnowledgePlanetWeeklyComputeService.formatCoveredWeekLabel(weekStart),
                "summary", plan.getSummary() == null ? "" : plan.getSummary(),
                "thinkDirections", formatList(plan.getThinkDirections()),
                "gapAreas", formatList(plan.getGapAreas()),
                "books", formatBooks(plan.getBookRecommendations()));
    }

    private static String formatList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "（暂无）";
        }
        return items.stream().map(s -> "· " + s).collect(Collectors.joining("\n"));
    }

    private static String formatBooks(List<KnowledgeWeeklyPlan.BookRecommendation> books) {
        if (books == null || books.isEmpty()) {
            return "（暂无）";
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeWeeklyPlan.BookRecommendation b : books) {
            sb.append("· 《")
                    .append(b.getTitle() == null ? "" : b.getTitle())
                    .append("》")
                    .append(b.getReason() == null ? "" : " — " + b.getReason());
            if (b.getUrl() != null && !b.getUrl().isBlank()) {
                sb.append(" ").append(b.getUrl().trim());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 管理端测试发信：不校验 {@code KNOWLEDGE_PLANET_WEEKLY_EMAIL_ENABLED}；幂等键带 {@code test:} 前缀以便重复测。
     *
     * @param planOverride 本次计算结果；非空时直接使用
     * @param updateInsightStatus 为 true 且库中存在对应周洞察行时，成功发信后标记为 SENT
     */
    public SingleWeeklyEmailResult sendTestForUser(
            long tenantId,
            long userId,
            LocalDate weekStart,
            KnowledgeWeeklyPlan planOverride,
            boolean updateInsightStatus) {
        if (!planetRuntime.isEnabled(tenantId)) {
            return new SingleWeeklyEmailResult("SKIPPED", "租户未启用知识星球", null);
        }
        if (!sceneReadinessQuery.isSceneConfigured(tenantId, MessageSceneCode.KNOWLEDGE_PLANET_WEEKLY)) {
            return new SingleWeeklyEmailResult("SKIPPED", "邮件通道未就绪", null);
        }
        if (planOverride == null) {
            return new SingleWeeklyEmailResult("SKIPPED", "无周报内容", null);
        }

        SecUserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return new SingleWeeklyEmailResult("SKIPPED", "用户未绑定邮箱", null);
        }
        var ineligible = recipientGate.skipReason(tenantId, userId);
        if (ineligible.isPresent()) {
            return new SingleWeeklyEmailResult("SKIPPED", ineligible.get(), null);
        }

        String tenantName = tenantRepository.findById(tenantId).map(t -> t.getName()).orElse("AI");
        try {
            Map<String, String> vars = buildVars(tenantName, user, weekStart, planOverride);
            String recipient = user.getEmail().trim();
            var result =
                    messageSendPort.send(
                            MessageSendRequest.builder()
                                    .tenantId(tenantId)
                                    .sceneCode(MessageSceneCode.KNOWLEDGE_PLANET_WEEKLY)
                                    .recipient(recipient)
                                    .templateVars(vars)
                                    .idempotencyKey(
                                            "kp-weekly-test:"
                                                    + tenantId
                                                    + ":"
                                                    + userId
                                                    + ":"
                                                    + weekStart
                                                    + ":"
                                                    + System.currentTimeMillis())
                                    .async(true)
                                    .build());
            if (result.getStatus() == MessageDeliveryStatus.FAILED) {
                throw new IllegalStateException(
                        result.getErrorMessage() == null ? "send failed" : result.getErrorMessage());
            }
            if (updateInsightStatus) {
                insightRepository
                        .findByUserWeek(tenantId, userId, weekStart)
                        .ifPresent(
                                ins -> {
                                    ins.setStatus(KnowledgeWeeklyInsightStatus.SENT);
                                    ins.setEmailedAt(LocalDateTime.now());
                                    ins.setErrorMessage(null);
                                    insightRepository.updateById(ins);
                                });
            }
            return new SingleWeeklyEmailResult("SENT", null, recipient);
        } catch (Exception ex) {
            log.warn("[知识星球] 测试邮件发送失败 tenantId={} userId={}", tenantId, userId, ex);
            if (updateInsightStatus) {
                insightRepository
                        .findByUserWeek(tenantId, userId, weekStart)
                        .ifPresent(
                                ins -> {
                                    ins.setStatus(KnowledgeWeeklyInsightStatus.FAILED);
                                    ins.setErrorMessage(
                                            ex.getMessage() == null ? "send failed" : ex.getMessage());
                                    insightRepository.updateById(ins);
                                });
            }
            return new SingleWeeklyEmailResult(
                    "FAILED", ex.getMessage() == null ? "send failed" : ex.getMessage(), null);
        }
    }

    public record WeeklyEmailResult(int sent, int skipped, int failed, String skipReason) {}

    public record SingleWeeklyEmailResult(String status, String message, String recipient) {}
}
