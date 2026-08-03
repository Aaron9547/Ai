package com.aaron.cloud.chat.knowledgeplanet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KnowledgePlanetWeeklyWeekLabelTest {

    @Test
    void formatCoveredWeekLabel_usesPriorNaturalWeek() {
        LocalDate insightWeekStart = LocalDate.of(2026, 6, 1);
        assertEquals("2026-05-25 至 2026-05-31", KnowledgePlanetWeeklyComputeService.formatCoveredWeekLabel(insightWeekStart));
    }
}
