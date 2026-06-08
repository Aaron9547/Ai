package com.aaron.cloud.common.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.rag.quality-assessment")
public class RagQualityAssessmentProperties {

    private boolean enabled = true;

    /** false 时仅跑本地 recall，不调 LLM judge */
    private boolean judgeEnabled = true;

    private int retentionDays = 180;

    private int topK = 10;
}
