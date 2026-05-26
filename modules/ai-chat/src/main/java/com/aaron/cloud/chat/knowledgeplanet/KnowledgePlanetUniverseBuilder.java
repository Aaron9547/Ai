package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.GraphLinkView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.GraphNodeView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.PlanetView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.UniverseGraphResponse;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将扁平知识节点聚合为「星系」视图：每颗 {@code planet} 对应一个主题簇（任意 topicTag 有交集的节点并到同一星球），其下挂载
 * {@code knowledge} 节点与轨道/关联连线。
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
        List<List<TenUserKnowledgeNode>> planetGroups = clusterBySharedTags(rows);

        List<PlanetView> planets = new ArrayList<>();
        List<GraphNodeView> nodes = new ArrayList<>();
        List<GraphLinkView> links = new ArrayList<>();
        int colorIdx = 0;

        for (List<TenUserKnowledgeNode> group : planetGroups) {
            List<TenUserKnowledgeNode> ordered = chronological(group);
            String category = planetLabelForGroup(ordered);
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

    /** 任意 topicTag 有交集的节点并到同一主题星球（传递闭包），避免相近话题被首标签微差拆散。 */
    private List<List<TenUserKnowledgeNode>> clusterBySharedTags(List<TenUserKnowledgeNode> rows) {
        int n = rows.size();
        if (n == 0) {
            return List.of();
        }
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (shareTag(rows.get(i), rows.get(j))) {
                    union(parent, i, j);
                }
            }
        }
        Map<Integer, List<TenUserKnowledgeNode>> byRoot = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            byRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(rows.get(i));
        }
        return byRoot.values().stream()
                .sorted(Comparator.comparingInt((List<TenUserKnowledgeNode> g) -> g.size()).reversed())
                .toList();
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }

    private String planetLabelForGroup(List<TenUserKnowledgeNode> group) {
        Map<String, Integer> counts = new HashMap<>();
        for (TenUserKnowledgeNode row : group) {
            String cat = primaryCategory(row);
            counts.merge(cat, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(UNCATEGORIZED);
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
                if (shareTag(a, b) || j == i + 1 || sameConversation(a, b)) {
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

    private static boolean sameConversation(TenUserKnowledgeNode a, TenUserKnowledgeNode b) {
        return a.getConversationId() != null
                && a.getConversationId().equals(b.getConversationId());
    }

    private String primaryCategory(TenUserKnowledgeNode row) {
        List<String> tags = parseTags(row.getTopicTagsJson());
        if (!tags.isEmpty()) {
            return tags.getFirst().trim();
        }
        return UNCATEGORIZED;
    }

    private boolean shareTag(TenUserKnowledgeNode a, TenUserKnowledgeNode b) {
        Set<String> ta = normalizedTagSet(a);
        Set<String> tb = normalizedTagSet(b);
        ta.retainAll(tb);
        return !ta.isEmpty();
    }

    private Set<String> normalizedTagSet(TenUserKnowledgeNode row) {
        return parseTags(row.getTopicTagsJson()).stream()
                .map(KnowledgePlanetUniverseBuilder::normalizeTag)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.trim();
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
