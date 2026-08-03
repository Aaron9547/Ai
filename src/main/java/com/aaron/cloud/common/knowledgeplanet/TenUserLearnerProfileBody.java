package com.aaron.cloud.common.knowledgeplanet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** {@code ten_user_learner_profile.body_json} 结构。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenUserLearnerProfileBody {

    private String roleOrStage = "";
    private List<String> coreInterests = new ArrayList<>();
    private Map<String, String> skillHints = new LinkedHashMap<>();
    private List<String> learningGoals = new ArrayList<>();
    private LocalDate lastUpdatedWeek;
}
