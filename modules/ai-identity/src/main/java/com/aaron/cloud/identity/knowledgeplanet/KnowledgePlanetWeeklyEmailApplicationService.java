package com.aaron.cloud.identity.knowledgeplanet;

import com.aaron.cloud.common.api.enums.profile.KnowledgeWeeklyInsightStatus;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import com.aaron.cloud.common.knowledgeplanet.TenUserWeeklyInsightRepository;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserWeeklyInsight;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.identity.mail.TenantTemplateEmailSender;
import com.aaron.cloud.identity.open.AuthRegisterVerificationConfig.EmailChannel;
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
    private final TenantKnowledgePlanetEmailResolver emailResolver;
    private final TenantTemplateEmailSender templateEmailSender;
    private final TenUserWeeklyInsightRepository insightRepository;
    private final SecUserAccountRepository userAccountRepository;
    private final SysTenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public WeeklyEmailResult sendForTenant(long tenantId, LocalDate weekStart) {
        if (!planetRuntime.isEnabled(tenantId)) {
            return new WeeklyEmailResult(0, 0, 0, "租户未启用知识星球");
        }
        KnowledgePlanetEmailConfig cfg = emailResolver.resolve(tenantId);
        if (!cfg.isEnabled() || !emailResolver.isDeliveryReady(tenantId)) {
            return new WeeklyEmailResult(0, 0, 0, "邮件通道未就绪");
        }

        String tenantName =
                tenantRepository.findById(tenantId).map(t -> t.getName()).orElse("AI");
        List<TenUserWeeklyInsight> ready = insightRepository.listReadyForWeek(tenantId, weekStart);
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        EmailChannel email = cfg.getEmail();

        for (TenUserWeeklyInsight ins : ready) {
            try {
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
                String subject =
                        TenantTemplateEmailSender.applyTemplate(email.getSubjectTemplate(), vars);
                String body = TenantTemplateEmailSender.applyTemplate(email.getBodyTemplate(), vars);
                templateEmailSender.send(email, user.getEmail().trim(), subject, body);
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
                "weekLabel", weekStart + " 起的一周",
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
                    .append(b.getReason() == null ? "" : " — " + b.getReason())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    public record WeeklyEmailResult(int sent, int skipped, int failed, String skipReason) {}
}
