package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgePlanetWeeklyBookEnrichSupportTest {

    private final KnowledgePlanetWeeklyBookEnrichSupport support = new KnowledgePlanetWeeklyBookEnrichSupport();

    @Test
    void enrichUrls_matchesReferenceByTitle() {
        var plan = new KnowledgeWeeklyPlan();
        var book = new KnowledgeWeeklyPlan.BookRecommendation();
        book.setTitle("Effective Java");
        book.setSource("web_search");
        plan.getBookRecommendations().add(book);

        List<WebSearchReference> refs =
                List.of(
                        WebSearchReference.ofTitleUrlSnippet(
                                "Effective Java 第3版", "https://example.com/ej", "snippet"));

        support.enrichUrls(plan, refs);

        assertEquals("https://example.com/ej", book.getUrl());
        assertTrue(book.getMatchedReferenceTitle().contains("Effective Java"));
    }
}
