package com.aaron.cloud.chat.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.chat.websearch.WebGroundingBundle;
import com.aaron.cloud.chat.websearch.WebSearchReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatWebSearchKnowledgeServiceTest {

    @Test
    void bundleForKnowledgePersistence_usesRefBulletsNotArkEssay() {
        String arkEssay = "以下是今日热点资讯：\n".repeat(50);
        WebGroundingBundle live =
                new WebGroundingBundle(
                        arkEssay,
                        List.of(
                                WebSearchReference.ofTitleUrlSnippet(
                                        "6G 试验频率获批", "https://example.com/6g", "工信部批复")));
        WebGroundingBundle stored = ChatWebSearchKnowledgeService.bundleForKnowledgePersistence(live);
        assertFalse(stored.summaryText().contains("以下是今日热点资讯"));
        assertTrue(stored.summaryText().contains("6G"));
        assertEquals(1, stored.references().size());
    }
}
