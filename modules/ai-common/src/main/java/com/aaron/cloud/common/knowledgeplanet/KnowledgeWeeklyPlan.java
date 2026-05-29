package com.aaron.cloud.common.knowledgeplanet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** {@code ten_user_weekly_insight.plan_json} 结构。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeWeeklyPlan {

    private String summary = "";
    private String inferredPersona = "";
    private List<String> evidenceTopics = new ArrayList<>();
    private String progressNotes = "";
    private List<String> thinkDirections = new ArrayList<>();
    private List<String> gapAreas = new ArrayList<>();
    private List<BookRecommendation> bookRecommendations = new ArrayList<>();
    private LearnerProfileDelta learnerProfileDelta = new LearnerProfileDelta();
    private String bookSearchQuery = "";

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookRecommendation {
        private String title = "";
        private String reason = "";
        private String url = "";
        /** web_search | classic */
        private String source = "";
        private String matchedReferenceTitle = "";
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LearnerProfileDelta {
        private String roleOrStage = "";
        private List<String> coreInterests = new ArrayList<>();
        private Map<String, String> skillHints = new LinkedHashMap<>();
        private List<String> learningGoals = new ArrayList<>();
    }
}
