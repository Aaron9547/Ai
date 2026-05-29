package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTopicTagsNormalizer;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyProgressLedger;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class KnowledgePlanetWeeklyStatsSupport {

    private static final com.fasterxml.jackson.databind.ObjectMapper TAG_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public List<String> topPlanetNames(List<TenUserKnowledgeNode> nodes, int limit) {
        Map<String, Long> counts = planetCounts(nodes);
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    public Map<String, Long> planetCounts(List<TenUserKnowledgeNode> nodes) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TenUserKnowledgeNode n : nodes) {
            primaryPlanet(n).ifPresent(p -> counts.merge(p, 1L, Long::sum));
        }
        return counts;
    }

    public Set<String> newPlanetsVsPrior(List<TenUserKnowledgeNode> thisWeek, List<TenUserKnowledgeNode> priorWeek) {
        Set<String> prior = new LinkedHashSet<>(topPlanetNames(priorWeek, 20));
        Set<String> current = new LinkedHashSet<>(topPlanetNames(thisWeek, 20));
        current.removeAll(prior);
        return current;
    }

    public List<String> repeatedGapAreas(KnowledgeWeeklyProgressLedger lastLedger, List<String> currentGaps) {
        if (lastLedger == null || lastLedger.getGapAreas() == null || currentGaps == null) {
            return List.of();
        }
        Set<String> last = lastLedger.getGapAreas().stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        List<String> out = new ArrayList<>();
        for (String g : currentGaps) {
            if (g != null && last.contains(g.trim())) {
                out.add(g.trim());
            }
        }
        return out;
    }

    public String formatWeekOverWeekBlock(
            int thisWeekNodeCount,
            int priorWeekNodeCount,
            Set<String> newPlanets,
            KnowledgeWeeklyProgressLedger lastLedger) {
        var sb = new StringBuilder();
        sb.append("【周环比（系统统计）】\n");
        sb.append("- 本周节点数：").append(thisWeekNodeCount);
        sb.append("；上周节点数：").append(priorWeekNodeCount).append('\n');
        if (!newPlanets.isEmpty()) {
            sb.append("- 本周新出现主题星球：").append(String.join("、", newPlanets)).append('\n');
        }
        if (lastLedger != null && lastLedger.getWeekStart() != null) {
            sb.append("- 上周摘要：")
                    .append(lastLedger.getSummaryOneLine() == null ? "" : lastLedger.getSummaryOneLine())
                    .append('\n');
            if (lastLedger.getGapAreas() != null && !lastLedger.getGapAreas().isEmpty()) {
                sb.append("- 上周短板：").append(String.join("、", lastLedger.getGapAreas())).append('\n');
            }
        }
        return sb.toString().trim();
    }

    public String formatPlanetSummaryBlock(List<TenUserKnowledgeNode> allRecent, int topN) {
        Map<String, Long> counts = planetCounts(allRecent);
        if (counts.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("【主题星球分布 TOP").append(topN).append("】\n");
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(topN)
                .forEach(e -> sb.append("- ").append(e.getKey()).append("：").append(e.getValue()).append(" 节点\n"));
        return sb.toString().trim();
    }

    private java.util.Optional<String> primaryPlanet(TenUserKnowledgeNode n) {
        if (n.getTopicTagsJson() == null || n.getTopicTagsJson().isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            List<String> tags = TAG_JSON.readValue(n.getTopicTagsJson(), new TypeReference<List<String>>() {});
            List<String> normalized = KnowledgePlanetTopicTagsNormalizer.normalize(tags);
            if (normalized.isEmpty()) {
                return java.util.Optional.empty();
            }
            String p = normalized.getFirst().trim();
            return p.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(p);
        } catch (Exception ex) {
            return java.util.Optional.empty();
        }
    }
}
