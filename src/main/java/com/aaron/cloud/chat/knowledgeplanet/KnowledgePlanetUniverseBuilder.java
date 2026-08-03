package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.GraphLinkView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.GraphNodeView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.PlanetView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.UniverseGraphResponse;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTopicTagsNormalizer;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将扁平知识节点聚合为「星系」视图：每颗 {@code planet} 对应 {@code topicTags[0]}（具体领域主题名），其下挂载
 * {@code knowledge} 节点与轨道/关联连线。宽范畴修饰应由沉淀 normalizer 排在子标签位，星图严格按 {@code [0]} 分星。
 */
@Component
@RequiredArgsConstructor
public class KnowledgePlanetUniverseBuilder {

    private static final String UNCATEGORIZED = "未分类";

    private static final int[] PLANET_PALETTE = {
        0x00f2fe, 0x4facfe, 0x7000ff, 0x00d9a0, 0xff6bcb, 0xffb347
    };

    private static final ObjectMapper TAG_JSON = new ObjectMapper();

    private final ObjectMapper objectMapper;

    public UniverseGraphResponse build(List<TenUserKnowledgeNode> rows) {
        List<List<TenUserKnowledgeNode>> planetGroups = clusterByPlanetTheme(rows);

        List<PlanetView> planets = new ArrayList<>();
        List<GraphNodeView> nodes = new ArrayList<>();
        List<GraphLinkView> links = new ArrayList<>();
        int colorIdx = 0;

        for (List<TenUserKnowledgeNode> group : planetGroups) {
            List<TenUserKnowledgeNode> ordered = chronological(group);
            String category = planetThemeKey(ordered.getFirst());
            String planetId = planetId(category);
            int color = PLANET_PALETTE[colorIdx % PLANET_PALETTE.length];
            colorIdx++;

            int density = ordered.size();
            double size = Math.min(28, 12 + density * 2.5);

            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < Math.min(3, ordered.size()); i++) {
                if (i > 0) summary.append("；");
                summary.append(ordered.get(i).getTitle());
            }
            if (ordered.size() > 3) {
                summary.append(" 等 ").append(ordered.size()).append(" 个知识点");
            }

            planets.add(
                    new PlanetView(
                            planetId,
                            category,
                            density,
                            color,
                            size,
                            summary.toString()));

            nodes.add(
                    new GraphNodeView(
                            planetId,
                            "planet",
                            null,
                            null,
                            category,
                            summary.toString(),
                            List.of(category)));

            for (TenUserKnowledgeNode n : ordered) {
                String nodeId = knowledgeNodeId(n.getId());
                nodes.add(
                        new GraphNodeView(
                                nodeId,
                                "knowledge",
                                planetId,
                                n.getId(),
                                n.getTitle(),
                                n.getSummary(),
                                parseTags(n.getTopicTagsJson())));
                links.add(new GraphLinkView(planetId, nodeId, "orbit"));
            }

            links.addAll(intraPlanetLinks(ordered));
        }

        return new UniverseGraphResponse(planets, nodes, links);
    }

    private List<List<TenUserKnowledgeNode>> clusterByPlanetTheme(List<TenUserKnowledgeNode> rows) {
        Map<String, List<TenUserKnowledgeNode>> byTheme = new LinkedHashMap<>();
        for (TenUserKnowledgeNode row : rows) {
            String key = planetThemeKey(row);
            byTheme.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        return byTheme.values().stream()
                .sorted(Comparator.comparingInt((List<TenUserKnowledgeNode> g) -> g.size()).reversed())
                .toList();
    }

    static String planetThemeKey(TenUserKnowledgeNode row) {
        List<String> tags = parseTagsStatic(row == null ? null : row.getTopicTagsJson());
        if (tags.isEmpty()) {
            return UNCATEGORIZED;
        }
        String first = normalizeTag(tags.getFirst());
        return first.isEmpty() ? UNCATEGORIZED : first;
    }

    private static List<TenUserKnowledgeNode> chronological(List<TenUserKnowledgeNode> group) {
        return group.stream()
                .sorted(
                        Comparator.comparing(
                                TenUserKnowledgeNode::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<GraphLinkView> intraPlanetLinks(List<TenUserKnowledgeNode> group) {
        List<GraphLinkView> out = new ArrayList<>();
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                TenUserKnowledgeNode a = group.get(i);
                TenUserKnowledgeNode b = group.get(j);
                if (shareSecondaryTag(a, b) || j == i + 1) {
                    out.add(
                            new GraphLinkView(
                                    knowledgeNodeId(a.getId()),
                                    knowledgeNodeId(b.getId()),
                                    "relation"));
                }
            }
        }
        return out;
    }

    private boolean shareSecondaryTag(TenUserKnowledgeNode a, TenUserKnowledgeNode b) {
        Set<String> ta = secondaryTagSet(a);
        Set<String> tb = secondaryTagSet(b);
        ta.retainAll(tb);
        return !ta.isEmpty();
    }

    private Set<String> secondaryTagSet(TenUserKnowledgeNode row) {
        List<String> tags = parseTags(row.getTopicTagsJson());
        if (tags.size() <= 1) {
            return Set.of();
        }
        return tags.subList(1, tags.size()).stream()
                .map(KnowledgePlanetUniverseBuilder::normalizeTag)
                .filter(t -> !t.isEmpty() && !KnowledgePlanetTopicTagsNormalizer.isBroadModifierTag(t))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.trim();
    }

    private List<String> parseTags(String json) {
        return parseTagsStatic(json);
    }

    static List<String> parseTagsStatic(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return TAG_JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String planetId(String category) {
        return "planet:" + category;
    }

    public static String knowledgeNodeId(long id) {
        return "node:" + id;
    }
}
