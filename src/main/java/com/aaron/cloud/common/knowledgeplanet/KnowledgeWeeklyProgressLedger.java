package com.aaron.cloud.common.knowledgeplanet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** {@code ten_user_weekly_insight.progress_ledger_json} 压缩结构，供下周 Prompt 印证。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeWeeklyProgressLedger {

    private LocalDate weekStart;
    private String summaryOneLine = "";
    private List<String> topPlanets = new ArrayList<>();
    private List<String> gapAreas = new ArrayList<>();
    private List<String> thinkDirections = new ArrayList<>();
}
