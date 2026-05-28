package com.aaron.cloud.chat.knowledgeplanet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.PlanetView;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.UniverseGraphResponse;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgePlanetUniverseBuilderTest {

    private final KnowledgePlanetUniverseBuilder builder =
            new KnowledgePlanetUniverseBuilder(new ObjectMapper());

    @Test
    void planetThemeKey_usesFirstTagOnly() {
        TenUserKnowledgeNode normalized =
                node(1L, tagsJson("油价", "汽柴油", "通识科普"));
        TenUserKnowledgeNode legacy =
                node(2L, tagsJson("通识科普", "油价", "汽柴油"));

        assertEquals("油价", KnowledgePlanetUniverseBuilder.planetThemeKey(normalized));
        assertEquals("通识科普", KnowledgePlanetUniverseBuilder.planetThemeKey(legacy));
    }

    @Test
    void build_separatesOilPriceFromHardwareOverclock() {
        TenUserKnowledgeNode oil =
                node(52L, tagsJson("油价", "汽柴油", "通识科普"));
        TenUserKnowledgeNode oc =
                node(54L, tagsJson("硬件超频", "电脑硬件", "超频"));

        UniverseGraphResponse graph = builder.build(List.of(oil, oc));

        List<String> planetNames =
                graph.planets().stream().map(PlanetView::name).sorted().toList();
        assertEquals(List.of("硬件超频", "油价"), planetNames);
    }

    @Test
    void build_doesNotMergeSixGWithHardware() {
        TenUserKnowledgeNode sixG = node(10L, tagsJson("6G", "通信", "通识科普"));
        TenUserKnowledgeNode oc = node(11L, tagsJson("硬件超频", "电脑硬件"));

        UniverseGraphResponse graph = builder.build(List.of(sixG, oc));

        assertEquals(2, graph.planets().size());
        assertNotEquals(
                graph.planets().getFirst().id(),
                graph.planets().get(1).id());
    }

    private static TenUserKnowledgeNode node(long id, String topicTagsJson) {
        TenUserKnowledgeNode n = new TenUserKnowledgeNode();
        n.setId(id);
        n.setTitle("t" + id);
        n.setSummary("s" + id);
        n.setTopicTagsJson(topicTagsJson);
        return n;
    }

    private static String tagsJson(String... tags) {
        try {
            return new ObjectMapper().writeValueAsString(List.of(tags));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
