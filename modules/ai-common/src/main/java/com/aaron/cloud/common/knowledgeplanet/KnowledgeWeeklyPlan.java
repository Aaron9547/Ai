package com.aaron.cloud.common.knowledgeplanet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** {@code ten_user_weekly_insight.plan_json} 结构。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeWeeklyPlan {

    private String summary = "";
    private List<String> thinkDirections = new ArrayList<>();
    private List<String> gapAreas = new ArrayList<>();
    private List<BookRecommendation> bookRecommendations = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookRecommendation {
        private String title = "";
        private String reason = "";
    }
}
