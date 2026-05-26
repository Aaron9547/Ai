package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.GraphLinkView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.GraphNodeView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.PlanetView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.UniverseGraphResponse;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将扁平知识节点聚合为「星系」视图：每颗 {@code planet} 对应一个主题分类（首条 topicTag），其下挂载 {@code knowledge} 节点与引力连线。
 */
@Component
@RequiredArgsConstructor
public class KnowledgePlanetUniverseBuilder {

    private static final String UNCATEGORIZED = "未分类";
    private static final int[] PLANET_PALETTE = {
        0x00f2fe, 0x4facfe, 0x7000ff, 0x00d9a0, 0xff6bcb, 0xffb347
    };

    private final ObjectMapper objectMapper;

    public UniverseGraphResponse build(List<TenUserKnowledgeNode> rows) {
        Map<String, List<TenUserKnowledgeNode>> byPlanet = new LinkedHashMap<>();
        for (TenUserKnowledgeNode row : rows) {
            String category = primaryCategory(row);
            byPlanet.computeIfAbsent(category, k -> new ArrayList<>()).add(row);
        }

        List<PlanetView> planets = new ArrayList<>();
        List<GraphNodeView> nodes = new ArrayList<>();
        List<GraphLinkView> links = new ArrayList<>();
        int colorIdx = 0;

        for (Map.Entry<String, List<TenUserKnowledgeNode>> entry : byPlanet.entrySet()) {
            String category = entry.getKey();
            List<TenUserKnowledgeNode> group = entry.getValue();
            String planetId = planetId(category);
            int color = PLANET_PALETTE[colorIdx % PLANET_PALETTE.length];
            colorIdx++;

            int density = group.size();
            double size = Math.min(28, 12 + density * 2.5);

            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < Math.min(3, group.size()); i++) {
                if (i > 0) summary.append("；");
                summary.append(group.get(i).getTitle());
            }
            if (group.size() > 3) {
                summary.append(" 等 ").append(group.size()).append(" 个知识点");
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

            for (TenUserKnowledgeNode n : group) {
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

            links.addAll(intraPlanetLinks(group));
        }

        return new UniverseGraphResponse(planets, nodes, links);
    }

    private List<GraphLinkView> intraPlanetLinks(List<TenUserKnowledgeNode> group) {
        List<GraphLinkView> out = new ArrayList<>();
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                TenUserKnowledgeNode a = group.get(i);
                TenUserKnowledgeNode b = group.get(j);
                if (shareTag(a, b) || j == i + 1) {
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

    private String primaryCategory(TenUserKnowledgeNode row) {
        List<String> tags = parseTags(row.getTopicTagsJson());
        if (!tags.isEmpty()) {
            return tags.getFirst().trim();
        }
        return UNCATEGORIZED;
    }

    private boolean shareTag(TenUserKnowledgeNode a, TenUserKnowledgeNode b) {
        Set<String> ta = parseTags(a.getTopicTagsJson()).stream().collect(Collectors.toSet());
        Set<String> tb = parseTags(b.getTopicTagsJson()).stream().collect(Collectors.toSet());
        ta.retainAll(tb);
        return !ta.isEmpty();
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
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
