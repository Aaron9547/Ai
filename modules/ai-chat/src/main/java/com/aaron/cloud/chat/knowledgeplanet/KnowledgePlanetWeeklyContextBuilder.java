package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.chat.knowledgeplanet.KnowledgePlanetWeeklyBookSearchSupport.BookSearchContext;
import com.aaron.cloud.common.api.enums.profile.ProfileTagCode;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyProgressLedger;
import com.aaron.cloud.common.knowledgeplanet.TenUserLearnerProfileBody;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.aaron.cloud.common.profile.TenProfileTagRepository;
import com.aaron.cloud.common.profile.TenUserMemoryAbstractRepository;
import com.aaron.cloud.common.profile.entity.TenProfileTag;
import com.aaron.cloud.common.util.TextClamp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyContextBuilder {

    private static final int NODE_SUMMARY_MAX = 120;
    private static final int MAX_WEEKLY_NODES = 12;

    private final KnowledgePlanetWeeklyStatsSupport statsSupport;
    private final TenUserMemoryAbstractRepository memoryAbstractRepository;
    private final TenProfileTagRepository profileTagRepository;
    private final ObjectMapper objectMapper;

    public record WeeklyBuiltContext(String userPayload, BookSearchContext bookSearch) {}

    public WeeklyBuiltContext build(
            TenantSnapshot snap,
            String subjectKey,
            long userId,
            List<TenUserKnowledgeNode> weekNodes,
            List<TenUserKnowledgeNode> priorWeekNodes,
            List<TenUserKnowledgeNode> recentAll,
            TenUserLearnerProfileBody learnerProfile,
            List<KnowledgeWeeklyProgressLedger> priorLedgers,
            KnowledgeWeeklyProgressLedger lastLedger,
            BookSearchContext bookSearch) {

        var body = new StringBuilder();

        appendLearningGoal(body, snap.getTenantId(), subjectKey);
        appendLearnerProfile(body, learnerProfile);
        appendWeekNodes(body, weekNodes, priorWeekNodes.isEmpty());
        appendPlanetSummary(body, recentAll);
        appendWeekOverWeek(body, weekNodes.size(), priorWeekNodes.size(), weekNodes, priorWeekNodes, lastLedger);
        appendPriorLedgers(body, priorLedgers);
        appendMemoryTrimmed(body, snap.getTenantId(), subjectKey);
        appendInterestTags(body, snap.getTenantId(), subjectKey);
        if (bookSearch != null && bookSearch.summaryBlock() != null && !bookSearch.summaryBlock().isBlank()) {
            body.append('\n').append(bookSearch.summaryBlock());
        }

        return new WeeklyBuiltContext(body.toString().trim(), bookSearch);
    }

    public String pickBookSearchTopic(
            List<TenUserKnowledgeNode> weekNodes,
            TenUserLearnerProfileBody learnerProfile,
            KnowledgeWeeklyProgressLedger lastLedger) {
        List<String> tops = statsSupport.topPlanetNames(weekNodes, 1);
        if (!tops.isEmpty()) {
            return tops.getFirst();
        }
        if (learnerProfile != null
                && learnerProfile.getCoreInterests() != null
                && !learnerProfile.getCoreInterests().isEmpty()) {
            return learnerProfile.getCoreInterests().getFirst();
        }
        if (lastLedger != null
                && lastLedger.getGapAreas() != null
                && !lastLedger.getGapAreas().isEmpty()) {
            return lastLedger.getGapAreas().getFirst();
        }
        if (lastLedger != null
                && lastLedger.getTopPlanets() != null
                && !lastLedger.getTopPlanets().isEmpty()) {
            return lastLedger.getTopPlanets().getFirst();
        }
        return "";
    }

    private void appendLearningGoal(StringBuilder body, long tenantId, String subjectKey) {
        profileTagRepository
                .find(tenantId, subjectKey, ProfileTagCode.LEARNING_GOAL)
                .map(TenProfileTag::getTagValue)
                .filter(v -> v != null && !v.isBlank())
                .ifPresent(
                        goal ->
                                body.append("【显式学习目标】\n")
                                        .append(TextClamp.ellipsis(goal.trim(), 400))
                                        .append("\n\n"));
    }

    private void appendLearnerProfile(StringBuilder body, TenUserLearnerProfileBody profile) {
        if (profile == null) {
            return;
        }
        boolean any =
                (profile.getRoleOrStage() != null && !profile.getRoleOrStage().isBlank())
                        || (profile.getCoreInterests() != null && !profile.getCoreInterests().isEmpty())
                        || (profile.getLearningGoals() != null && !profile.getLearningGoals().isEmpty());
        if (!any) {
            return;
        }
        body.append("【学习者画像快照】\n");
        if (profile.getRoleOrStage() != null && !profile.getRoleOrStage().isBlank()) {
            body.append("- 阶段/角色：").append(profile.getRoleOrStage().trim()).append('\n');
        }
        if (profile.getCoreInterests() != null && !profile.getCoreInterests().isEmpty()) {
            body.append("- 核心兴趣：").append(String.join("、", profile.getCoreInterests())).append('\n');
        }
        if (profile.getLearningGoals() != null && !profile.getLearningGoals().isEmpty()) {
            body.append("- 学习目标：").append(String.join("、", profile.getLearningGoals())).append('\n');
        }
        body.append('\n');
    }

    private void appendWeekNodes(StringBuilder body, List<TenUserKnowledgeNode> weekNodes, boolean noPriorFallback) {
        body.append("【本周知识节点】\n");
        if (weekNodes.isEmpty()) {
            body.append(noPriorFallback ? "（本周暂无新节点）\n\n" : "（本周暂无新节点，以下为近期回顾）\n\n");
            return;
        }
        int n = 0;
        for (TenUserKnowledgeNode node : weekNodes) {
            if (n >= MAX_WEEKLY_NODES) {
                break;
            }
            body.append("- ")
                    .append(node.getTitle())
                    .append("：")
                    .append(TextClamp.ellipsis(node.getSummary(), NODE_SUMMARY_MAX))
                    .append('\n');
            n++;
        }
        body.append('\n');
    }

    private void appendPlanetSummary(StringBuilder body, List<TenUserKnowledgeNode> recentAll) {
        String block = statsSupport.formatPlanetSummaryBlock(recentAll, 5);
        if (!block.isBlank()) {
            body.append(block).append("\n\n");
        }
    }

    private void appendWeekOverWeek(
            StringBuilder body,
            int thisCount,
            int priorCount,
            List<TenUserKnowledgeNode> thisWeek,
            List<TenUserKnowledgeNode> priorWeek,
            KnowledgeWeeklyProgressLedger lastLedger) {
        var newPlanets = statsSupport.newPlanetsVsPrior(thisWeek, priorWeek);
        String block = statsSupport.formatWeekOverWeekBlock(thisCount, priorCount, newPlanets, lastLedger);
        if (!block.isBlank()) {
            body.append(block).append("\n\n");
        }
    }

    private void appendPriorLedgers(StringBuilder body, List<KnowledgeWeeklyProgressLedger> ledgers) {
        if (ledgers == null || ledgers.isEmpty()) {
            return;
        }
        body.append("【近几周进度账本】\n");
        for (KnowledgeWeeklyProgressLedger l : ledgers) {
            if (l == null || l.getWeekStart() == null) {
                continue;
            }
            body.append("- ").append(l.getWeekStart()).append("：");
            body.append(l.getSummaryOneLine() == null ? "" : l.getSummaryOneLine());
            if (l.getTopPlanets() != null && !l.getTopPlanets().isEmpty()) {
                body.append("（主题：").append(String.join("、", l.getTopPlanets())).append("）");
            }
            body.append('\n');
        }
        body.append('\n');
    }

    private void appendMemoryTrimmed(StringBuilder body, long tenantId, String subjectKey) {
        memoryAbstractRepository
                .findByTenantAndSubject(tenantId, subjectKey)
                .map(a -> a.getBodyJson())
                .filter(j -> j != null && !j.isBlank())
                .ifPresent(
                        json -> {
                            String trimmed = trimMemoryAbstract(json);
                            if (!trimmed.isBlank()) {
                                body.append("【记忆要点】\n").append(trimmed).append("\n\n");
                            }
                        });
    }

    private String trimMemoryAbstract(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            var sb = new StringBuilder();
            appendJsonStringField(sb, root, "working_summary");
            appendJsonArrayField(sb, root, "stable_facts", "稳定事实");
            appendJsonArrayField(sb, root, "episodic_hooks", "近期话题");
            JsonNode delta = root.get("profile_delta");
            if (delta != null && delta.isObject() && !delta.isEmpty()) {
                sb.append("- 画像增量：").append(TextClamp.ellipsis(delta.toString(), 400)).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception ex) {
            return TextClamp.ellipsis(json, 800);
        }
    }

    private static void appendJsonStringField(StringBuilder sb, JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n != null && n.isTextual() && !n.asText().isBlank()) {
            sb.append("- ").append(n.asText().trim()).append('\n');
        }
    }

    private static void appendJsonArrayField(StringBuilder sb, JsonNode root, String field, String label) {
        JsonNode arr = root.get(field);
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return;
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : arr) {
            if (item.isTextual() && !item.asText().isBlank()) {
                items.add(item.asText().trim());
            }
            if (items.size() >= 6) {
                break;
            }
        }
        if (!items.isEmpty()) {
            sb.append("- ").append(label).append("：").append(String.join("；", items)).append('\n');
        }
    }

    private void appendInterestTags(StringBuilder body, long tenantId, String subjectKey) {
        profileTagRepository
                .find(tenantId, subjectKey, ProfileTagCode.INTEREST_NEWS_JSON)
                .map(TenProfileTag::getTagValue)
                .filter(v -> v != null && !v.isBlank())
                .ifPresent(
                        raw -> {
                            List<String> labels = parseInterestLabels(raw);
                            if (!labels.isEmpty()) {
                                body.append("【资讯兴趣标签】\n")
                                        .append(String.join("、", labels))
                                        .append("\n\n");
                            }
                        });
    }

    private List<String> parseInterestLabels(String raw) {
        try {
            JsonNode arr = objectMapper.readTree(raw);
            if (!arr.isArray()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode n : arr) {
                JsonNode tag = n.get("tag");
                if (tag != null && tag.isTextual() && !tag.asText().isBlank()) {
                    out.add(tag.asText().trim());
                }
                if (out.size() >= 10) {
                    break;
                }
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }
}
