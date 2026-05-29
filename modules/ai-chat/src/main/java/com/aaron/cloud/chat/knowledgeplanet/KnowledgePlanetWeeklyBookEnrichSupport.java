package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgePlanetWeeklyBookEnrichSupport {

    public void enrichUrls(KnowledgeWeeklyPlan plan, List<WebSearchReference> references) {
        if (plan == null || plan.getBookRecommendations() == null || references == null || references.isEmpty()) {
            return;
        }
        for (KnowledgeWeeklyPlan.BookRecommendation book : plan.getBookRecommendations()) {
            if (book == null) {
                continue;
            }
            if (book.getUrl() != null && !book.getUrl().isBlank()) {
                continue;
            }
            String title = book.getTitle() == null ? "" : book.getTitle().trim();
            if (title.isEmpty()) {
                continue;
            }
            WebSearchReference match = findBestMatch(title, references);
            if (match != null && match.url() != null && !match.url().isBlank()) {
                book.setUrl(match.url().trim());
                book.setMatchedReferenceTitle(match.title());
                if (book.getSource() == null || book.getSource().isBlank()) {
                    book.setSource("web_search");
                }
            }
        }
        if (plan.getBookSearchQuery() == null) {
            plan.setBookSearchQuery("");
        }
    }

    private static WebSearchReference findBestMatch(String title, List<WebSearchReference> references) {
        String normTitle = normalize(title);
        WebSearchReference contains = null;
        WebSearchReference reverse = null;
        for (WebSearchReference r : references) {
            if (r.title() == null || r.title().isBlank()) {
                continue;
            }
            String normRef = normalize(r.title());
            if (normRef.equals(normTitle)) {
                return r;
            }
            if (normRef.contains(normTitle) || normTitle.contains(normRef)) {
                if (contains == null || normRef.length() < normalize(contains.title()).length()) {
                    contains = r;
                }
            }
        }
        return contains != null ? contains : reverse;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("《", "")
                .replace("》", "")
                .replace(" ", "")
                .replace("\u3000", "")
                .toLowerCase()
                .trim();
    }
}
