package com.aaron.cloud.common.profile.admin;

import com.aaron.cloud.common.api.enums.profile.ProfileTagCode;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import com.aaron.cloud.common.knowledgeplanet.TenUserWeeklyInsightRepository;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserWeeklyInsight;
import com.aaron.cloud.common.profile.TenProfileTagRepository;
import com.aaron.cloud.common.profile.entity.TenProfileTag;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 管理端：知识星球周报「是否有帮助」反馈（{@code ten_profile_tag.WEEKLY_INSIGHT_FEEDBACK_JSON}）。 */
@Service
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyFeedbackAdminApplicationService {

    private final TenProfileTagRepository tenProfileTagRepository;
    private final TenUserWeeklyInsightRepository weeklyInsightRepository;
    private final SysTenantMemberRepository tenantMemberRepository;
    private final SecUserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;

    /** 按用户与 {@code week_start} 读取已落库的周报方案（供反馈列表弹窗展示）。 */
    public WeeklyInsightDetailView getWeeklyInsight(long tenantId, long userId, LocalDate weekStart) {
        tenantMemberRepository
                .find(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不在当前租户"));
        TenUserWeeklyInsight ins =
                weeklyInsightRepository
                        .findByUserWeek(tenantId, userId, weekStart)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "该周暂无已生成的成长方案"));
        KnowledgeWeeklyPlan plan;
        try {
            plan = objectMapper.readValue(ins.getPlanJson(), KnowledgeWeeklyPlan.class);
        } catch (Exception ex) {
            plan = new KnowledgeWeeklyPlan();
        }
        LocalDateTime computedAt = ins.getComputedAt();
        return new WeeklyInsightDetailView(
                ins.getWeekStart().toString(),
                ins.getStatus() == null ? "" : ins.getStatus().name(),
                plan,
                computedAt == null ? null : computedAt.toString());
    }

    public WeeklyFeedbackPageResult page(long tenantId, long page, long size, String keyword) {
        long pageNo = Math.max(1, page);
        long pageSize = Math.min(100, Math.max(1, size));
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        List<TenProfileTag> tags =
                tenProfileTagRepository.listByTenantAndTagCode(
                        tenantId, ProfileTagCode.WEEKLY_INSIGHT_FEEDBACK_JSON);
        List<WeeklyFeedbackEntryRow> all = new ArrayList<>();
        for (TenProfileTag tag : tags) {
            Optional<Long> userId = parseUserId(tag.getSubjectKey());
            if (userId.isEmpty()) {
                continue;
            }
            SecUserAccount user = userAccountRepository.findById(userId.get()).orElse(null);
            String loginName = user == null || user.getLoginName() == null ? "" : user.getLoginName();
            String displayName = user == null || user.getDisplayName() == null ? "" : user.getDisplayName();
            if (!kw.isEmpty()) {
                String hay = (loginName + " " + displayName).toLowerCase(Locale.ROOT);
                if (!hay.contains(kw)) {
                    continue;
                }
            }
            for (ParsedEntry e : parseEntries(tag.getTagValue())) {
                all.add(
                        new WeeklyFeedbackEntryRow(
                                userId.get(),
                                loginName,
                                displayName,
                                e.weekStart(),
                                e.helpful(),
                                e.at()));
            }
        }
        all.sort(
                Comparator.comparing(WeeklyFeedbackEntryRow::at, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WeeklyFeedbackEntryRow::userId));

        long helpfulCount = all.stream().filter(WeeklyFeedbackEntryRow::helpful).count();
        long notHelpfulCount = all.size() - helpfulCount;

        long total = all.size();
        int from = (int) ((pageNo - 1) * pageSize);
        if (from >= total) {
            return new WeeklyFeedbackPageResult(
                    List.of(), total, pageNo, pageSize, helpfulCount, notHelpfulCount);
        }
        int to = (int) Math.min(total, from + pageSize);
        return new WeeklyFeedbackPageResult(
                all.subList(from, to), total, pageNo, pageSize, helpfulCount, notHelpfulCount);
    }

    private static Optional<Long> parseUserId(String subjectKey) {
        if (subjectKey == null || !subjectKey.startsWith("u:")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(subjectKey.substring(2).trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private List<ParsedEntry> parseEntries(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<ParsedEntry> out = new ArrayList<>();
            for (JsonNode n : root) {
                if (!n.isObject()) {
                    continue;
                }
                String weekStart = n.path("weekStart").asText("");
                boolean helpful = n.path("helpful").asBoolean(false);
                String at = n.path("at").asText("");
                if (weekStart.isBlank() && at.isBlank()) {
                    continue;
                }
                out.add(new ParsedEntry(weekStart, helpful, at.isBlank() ? null : at));
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private record ParsedEntry(String weekStart, boolean helpful, String at) {}

    public record WeeklyFeedbackEntryRow(
            long userId, String loginName, String displayName, String weekStart, boolean helpful, String at) {}

    public record WeeklyFeedbackPageResult(
            List<WeeklyFeedbackEntryRow> records,
            long total,
            long page,
            long size,
            long helpfulCount,
            long notHelpfulCount) {}

    public record WeeklyInsightDetailView(
            String weekStart, String status, KnowledgeWeeklyPlan plan, String computedAt) {}
}
