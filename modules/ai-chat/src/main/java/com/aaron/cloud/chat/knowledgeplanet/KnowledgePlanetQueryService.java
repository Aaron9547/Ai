package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.NodeSummaryView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.SummaryResponse;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.UniverseGraphResponse;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.WeeklyLatestResponse;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantContextHolder.TenantSnapshot;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import com.aaron.cloud.common.knowledgeplanet.TenUserKnowledgeNodeRepository;
import com.aaron.cloud.common.knowledgeplanet.TenUserWeeklyInsightRepository;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class KnowledgePlanetQueryService {

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final TenUserKnowledgeNodeRepository nodeRepository;
    private final TenUserWeeklyInsightRepository insightRepository;
    private final KnowledgePlanetUniverseBuilder universeBuilder;
    private final ObjectMapper objectMapper;

    public SummaryResponse summary() {
        TenantSnapshot snap = TenantContextHolder.require();
        if (!planetRuntime.isEnabled(snap.getTenantId())) {
            return SummaryResponse.disabled();
        }
        String subjectKey = requireSubject(snap);
        long tenantId = snap.getTenantId();
        List<TenUserKnowledgeNode> all = nodeRepository.listRecent(tenantId, subjectKey, 80);
        UniverseGraphResponse universe = universeBuilder.build(all);

        List<TenUserKnowledgeNode> recent = nodeRepository.listRecent(tenantId, subjectKey, 5);
        List<NodeSummaryView> nodes =
                recent.stream()
                        .map(
                                n ->
                                        new NodeSummaryView(
                                                n.getId(),
                                                n.getTitle(),
                                                n.getSummary(),
                                                n.getCreatedAt()))
                        .toList();

        String dominant =
                universe.planets().stream()
                        .max(Comparator.comparingInt(p -> p.nodeCount()))
                        .map(p -> p.name())
                        .orElse("");

        String weeklySummary = "";
        if (snap.getUserId() != null) {
            weeklySummary =
                    insightRepository
                            .findLatestForUser(tenantId, snap.getUserId())
                            .map(
                                    ins -> {
                                        try {
                                            KnowledgeWeeklyPlan p =
                                                    objectMapper.readValue(
                                                            ins.getPlanJson(), KnowledgeWeeklyPlan.class);
                                            return p.getSummary() == null ? "" : p.getSummary();
                                        } catch (Exception e) {
                                            return "";
                                        }
                                    })
                            .orElse("");
        }
        return new SummaryResponse(
                true,
                all.size(),
                universe.planets().size(),
                dominant,
                nodes,
                weeklySummary,
                snap.getUserId() != null);
    }

    public UniverseGraphResponse universeGraph() {
        TenantSnapshot snap = TenantContextHolder.require();
        if (!planetRuntime.isEnabled(snap.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge_planet_disabled");
        }
        String subjectKey = requireSubject(snap);
        List<TenUserKnowledgeNode> nodes =
                nodeRepository.listRecent(snap.getTenantId(), subjectKey, 80);
        return universeBuilder.build(nodes);
    }

    public WeeklyLatestResponse weeklyLatest() {
        TenantSnapshot snap = TenantContextHolder.require();
        if (snap.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login_required");
        }
        if (!planetRuntime.isEnabled(snap.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge_planet_disabled");
        }
        return insightRepository
                .findLatestForUser(snap.getTenantId(), snap.getUserId())
                .map(
                        ins -> {
                            try {
                                KnowledgeWeeklyPlan plan =
                                        objectMapper.readValue(ins.getPlanJson(), KnowledgeWeeklyPlan.class);
                                return new WeeklyLatestResponse(
                                        ins.getWeekStart(),
                                        ins.getStatus().name(),
                                        plan);
                            } catch (Exception e) {
                                return new WeeklyLatestResponse(
                                        ins.getWeekStart(), ins.getStatus().name(), new KnowledgeWeeklyPlan());
                            }
                        })
                .orElse(new WeeklyLatestResponse(null, null, null));
    }

    private String requireSubject(TenantSnapshot snap) {
        String sk = ProfileSubjectKey.fromSnapshot(snap);
        if (sk == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject_required");
        }
        return sk;
    }
}
