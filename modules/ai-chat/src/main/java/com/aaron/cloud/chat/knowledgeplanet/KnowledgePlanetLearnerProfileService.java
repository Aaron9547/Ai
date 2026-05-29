package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyProgressLedger;
import com.aaron.cloud.common.knowledgeplanet.TenUserLearnerProfileBody;
import com.aaron.cloud.common.knowledgeplanet.TenUserLearnerProfileRepository;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserLearnerProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgePlanetLearnerProfileService {

    private final TenUserLearnerProfileRepository repository;
    private final ObjectMapper objectMapper;

    public TenUserLearnerProfileBody readBody(long tenantId, long userId) {
        return repository
                .findByUser(tenantId, userId)
                .map(p -> parseBody(p.getBodyJson()))
                .orElse(new TenUserLearnerProfileBody());
    }

    public void mergeDelta(long tenantId, long userId, LocalDate weekStart, KnowledgeWeeklyPlan.LearnerProfileDelta delta) {
        if (delta == null) {
            return;
        }
        TenUserLearnerProfileBody body = readBody(tenantId, userId);
        if (delta.getRoleOrStage() != null && !delta.getRoleOrStage().isBlank()) {
            body.setRoleOrStage(delta.getRoleOrStage().trim());
        }
        if (delta.getCoreInterests() != null && !delta.getCoreInterests().isEmpty()) {
            body.setCoreInterests(mergeStringList(body.getCoreInterests(), delta.getCoreInterests(), 12));
        }
        if (delta.getSkillHints() != null && !delta.getSkillHints().isEmpty()) {
            Map<String, String> merged = body.getSkillHints() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body.getSkillHints());
            delta.getSkillHints().forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null && !v.isBlank()) {
                    merged.put(k.trim(), v.trim());
                }
            });
            body.setSkillHints(merged);
        }
        if (delta.getLearningGoals() != null && !delta.getLearningGoals().isEmpty()) {
            body.setLearningGoals(mergeStringList(body.getLearningGoals(), delta.getLearningGoals(), 8));
        }
        body.setLastUpdatedWeek(weekStart);

        var row =
                repository
                        .findByUser(tenantId, userId)
                        .orElseGet(
                                () -> {
                                    var n = new TenUserLearnerProfile();
                                    n.setTenantId(tenantId);
                                    n.setUserId(userId);
                                    return n;
                                });
        try {
            row.setBodyJson(objectMapper.writeValueAsString(body));
        } catch (Exception ex) {
            return;
        }
        row.setUpdatedWeekStart(weekStart);
        if (row.getId() == null) {
            repository.insert(row);
        } else {
            repository.updateById(row);
        }
    }

    public KnowledgeWeeklyProgressLedger buildLedger(LocalDate weekStart, KnowledgeWeeklyPlan plan) {
        var ledger = new KnowledgeWeeklyProgressLedger();
        ledger.setWeekStart(weekStart);
        if (plan == null) {
            return ledger;
        }
        ledger.setSummaryOneLine(plan.getSummary() == null ? "" : plan.getSummary().trim());
        if (plan.getEvidenceTopics() != null) {
            ledger.setTopPlanets(new ArrayList<>(plan.getEvidenceTopics()));
        }
        if (plan.getGapAreas() != null) {
            ledger.setGapAreas(new ArrayList<>(plan.getGapAreas()));
        }
        if (plan.getThinkDirections() != null) {
            ledger.setThinkDirections(new ArrayList<>(plan.getThinkDirections()));
        }
        return ledger;
    }

    private TenUserLearnerProfileBody parseBody(String json) {
        if (json == null || json.isBlank()) {
            return new TenUserLearnerProfileBody();
        }
        try {
            TenUserLearnerProfileBody b = objectMapper.readValue(json, TenUserLearnerProfileBody.class);
            return b == null ? new TenUserLearnerProfileBody() : b;
        } catch (Exception ex) {
            return new TenUserLearnerProfileBody();
        }
    }

    private static List<String> mergeStringList(List<String> base, List<String> delta, int cap) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        if (base != null) {
            for (String s : base) {
                if (s == null || s.isBlank()) {
                    continue;
                }
                String t = s.trim();
                if (seen.add(t)) {
                    out.add(t);
                }
            }
        }
        for (String s : delta) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String t = s.trim();
            if (seen.add(t)) {
                out.add(t);
            }
        }
        if (out.size() > cap) {
            return new ArrayList<>(out.subList(0, cap));
        }
        return out;
    }
}
