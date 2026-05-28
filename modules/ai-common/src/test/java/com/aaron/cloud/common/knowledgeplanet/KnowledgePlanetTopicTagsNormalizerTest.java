package com.aaron.cloud.common.knowledgeplanet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgePlanetTopicTagsNormalizerTest {

    @Test
    void normalize_movesBroadModifierFromHeadToTail() {
        assertEquals(
                List.of("油价", "汽柴油", "通识科普"),
                KnowledgePlanetTopicTagsNormalizer.normalize(
                        List.of("通识科普", "油价", "汽柴油")));
    }

    @Test
    void normalize_keepsCorrectOrder() {
        List<String> tags = List.of("硬件超频", "电脑硬件", "超频");
        assertEquals(tags, KnowledgePlanetTopicTagsNormalizer.normalize(tags));
    }

    @Test
    void normalize_dedupesAndTrims() {
        assertEquals(
                List.of("油价", "汽柴油"),
                KnowledgePlanetTopicTagsNormalizer.normalize(List.of(" 油价 ", "油价", "汽柴油", "")));
    }

    @Test
    void isBroadModifierTag_suffixAndPlaceholder() {
        assertTrue(KnowledgePlanetTopicTagsNormalizer.isBroadModifierTag("通识科普"));
        assertTrue(KnowledgePlanetTopicTagsNormalizer.isBroadModifierTag("生活常识"));
        assertFalse(KnowledgePlanetTopicTagsNormalizer.isBroadModifierTag("硬件超频"));
        assertFalse(KnowledgePlanetTopicTagsNormalizer.isBroadModifierTag("油价"));
    }
}
