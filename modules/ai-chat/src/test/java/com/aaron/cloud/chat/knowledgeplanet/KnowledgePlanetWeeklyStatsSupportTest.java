package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgePlanetWeeklyStatsSupportTest {

    private final KnowledgePlanetWeeklyStatsSupport support = new KnowledgePlanetWeeklyStatsSupport();

    @Test
    void topPlanetNames_countsPrimaryTag() throws Exception {
        var n1 = node("[\"排序算法\",\"Java\"]");
        var n2 = node("[\"排序算法\",\"快排\"]");
        var n3 = node("[\"JVM\",\"GC\"]");

        List<String> tops = support.topPlanetNames(List.of(n1, n2, n3), 2);
        assertEquals("排序算法", tops.getFirst());
        assertEquals(2, tops.size());
    }

    @Test
    void newPlanetsVsPrior_detectsNewTheme() throws Exception {
        var prior = node("[\"油价\",\"柴油\"]");
        var now = node("[\"排序算法\",\"红黑树\"]");

        var added = support.newPlanetsVsPrior(List.of(now), List.of(prior));
        assertTrue(added.contains("排序算法"));
    }

    private static TenUserKnowledgeNode node(String tagsJson) {
        var n = new TenUserKnowledgeNode();
        n.setTopicTagsJson(tagsJson);
        n.setTitle("t");
        n.setSummary("s");
        return n;
    }
}
