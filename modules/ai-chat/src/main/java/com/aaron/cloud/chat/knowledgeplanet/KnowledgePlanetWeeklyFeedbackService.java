package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.common.api.enums.profile.ProfileTagCode;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.TenProfileTagRepository;
import com.aaron.cloud.common.profile.entity.TenProfileTag;
import com.aaron.cloud.common.time.BeijingTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyFeedbackService {

    private static final int MAX_FEEDBACK_ENTRIES = 24;

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final TenProfileTagRepository profileTagRepository;
    private final ObjectMapper objectMapper;

    public void recordFeedback(boolean helpful, LocalDate planWeekStart) {
        TenantSnapshot snap = TenantContextHolder.require();
        if (snap.getUserId() == null) {
            throw new IllegalStateException("login_required");
        }
        if (!planetRuntime.isEnabled(snap.getTenantId())) {
            return;
        }
        String subjectKey = ProfileSubjectKey.userKey(snap.getUserId());
        long tenantId = snap.getTenantId();
        LocalDate week =
                planWeekStart != null
                        ? planWeekStart
                        : BeijingTime.today().with(java.time.DayOfWeek.MONDAY);
        try {
            appendFeedback(tenantId, subjectKey, week, helpful);
        } catch (Exception ex) {
            log.warn("[知识星球] 周报反馈失败 tenantId={} userId={}", tenantId, snap.getUserId(), ex);
        }
    }

    /** 读取指定方案周（{@code week_start}）是否已反馈；无记录则 empty。 */
    public java.util.Optional<Boolean> findHelpfulForWeek(
            long tenantId, String subjectKey, LocalDate weekStart) {
        if (weekStart == null) {
            return java.util.Optional.empty();
        }
        return profileTagRepository
                .find(tenantId, subjectKey, ProfileTagCode.WEEKLY_INSIGHT_FEEDBACK_JSON)
                .flatMap(
                        tag -> {
                            try {
                                return parseHelpfulForWeek(tag.getTagValue(), weekStart.toString());
                            } catch (Exception ex) {
                                return java.util.Optional.empty();
                            }
                        });
    }

    private java.util.Optional<Boolean> parseHelpfulForWeek(String json, String weekKey) throws Exception {
        if (json == null || json.isBlank()) {
            return java.util.Optional.empty();
        }
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray()) {
            return java.util.Optional.empty();
        }
        for (JsonNode node : root) {
            if (node != null
                    && node.isObject()
                    && weekKey.equals(node.path("weekStart").asText())) {
                return java.util.Optional.of(node.path("helpful").asBoolean(false));
            }
        }
        return java.util.Optional.empty();
    }

    public void saveLearningGoal(String goal) {
        TenantSnapshot snap = TenantContextHolder.require();
        if (snap.getUserId() == null) {
            throw new IllegalStateException("login_required");
        }
        long tenantId = snap.getTenantId();
        String subjectKey = ProfileSubjectKey.userKey(snap.getUserId());
        String value = goal == null ? "" : goal.trim();
        var existing = profileTagRepository.find(tenantId, subjectKey, ProfileTagCode.LEARNING_GOAL);
        if (value.isEmpty()) {
            existing.ifPresent(
                    t -> {
                        t.setTagValue("");
                        profileTagRepository.updateById(t);
                    });
            return;
        }
        if (existing.isPresent()) {
            TenProfileTag u = existing.get();
            u.setTagValue(value);
            profileTagRepository.updateById(u);
        } else {
            var n = new TenProfileTag();
            n.setTenantId(tenantId);
            n.setSubjectKey(subjectKey);
            n.setTagCode(ProfileTagCode.LEARNING_GOAL);
            n.setTagValue(value);
            profileTagRepository.insert(n);
        }
    }

    private void appendFeedback(long tenantId, String subjectKey, LocalDate weekStart, boolean helpful)
            throws Exception {
        var existing =
                profileTagRepository.find(tenantId, subjectKey, ProfileTagCode.WEEKLY_INSIGHT_FEEDBACK_JSON);
        ArrayNode arr = objectMapper.createArrayNode();
        if (existing.isPresent() && existing.get().getTagValue() != null && !existing.get().getTagValue().isBlank()) {
            try {
                arr = (ArrayNode) objectMapper.readTree(existing.get().getTagValue());
            } catch (Exception ignored) {
                arr = objectMapper.createArrayNode();
            }
        }
        String weekKey = weekStart.toString();
        for (int i = 0; i < arr.size(); i++) {
            JsonNode node = arr.get(i);
            if (node != null && node.isObject() && weekKey.equals(node.path("weekStart").asText())) {
                arr.remove(i);
                break;
            }
        }
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("weekStart", weekKey);
        entry.put("helpful", helpful);
        entry.put("at", java.time.Instant.now().toString());
        arr.insert(0, entry);
        while (arr.size() > MAX_FEEDBACK_ENTRIES) {
            arr.remove(arr.size() - 1);
        }
        String json = objectMapper.writeValueAsString(arr);
        if (existing.isPresent()) {
            TenProfileTag u = existing.get();
            u.setTagValue(json);
            profileTagRepository.updateById(u);
        } else {
            var n = new TenProfileTag();
            n.setTenantId(tenantId);
            n.setSubjectKey(subjectKey);
            n.setTagCode(ProfileTagCode.WEEKLY_INSIGHT_FEEDBACK_JSON);
            n.setTagValue(json);
            profileTagRepository.insert(n);
        }
    }
}
