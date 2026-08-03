package com.aaron.cloud.gateway.observability;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.observability.ObsMcpTraceEventRepository;
import com.aaron.cloud.common.observability.ObsRagHitEventRepository;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.aaron.cloud.common.rag.RagQualityAssessmentRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 可观测性与 RAG 质量报告 retention：分批 DELETE，避免长锁。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObservabilityRetentionScheduledTask {

    private final PlatformSettingApplicationService platformSettings;
    private final ObsMcpTraceEventRepository mcpTraceEventRepository;
    private final ObsRagHitEventRepository ragHitEventRepository;
    private final RagQualityAssessmentRepository ragQualityAssessmentRepository;

    @Scheduled(cron = "0 * * * * *")
    public void purgeObsEventsIfDue() {
        if (!platformSettings.isCronDue(PlatformSettingKey.OBSERVABILITY_RETENTION_CRON)) {
            return;
        }
        if (!platformSettings.getBoolean(PlatformSettingKey.OBSERVABILITY_ENABLED)) {
            return;
        }
        LocalDateTime cutoff =
                LocalDateTime.now()
                        .minusDays(platformSettings.getInt(PlatformSettingKey.OBSERVABILITY_RETENTION_DAYS));
        int batch = Math.max(100, platformSettings.getInt(PlatformSettingKey.OBSERVABILITY_PURGE_BATCH_SIZE));
        int mcp = purgeLoop(() -> mcpTraceEventRepository.deleteOlderThan(cutoff, batch));
        int rag = purgeLoop(() -> ragHitEventRepository.deleteOlderThan(cutoff, batch));
        if (mcp > 0 || rag > 0) {
            log.info("obs retention deleted mcp={} ragHits={}", mcp, rag);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void purgeQualityReportsIfDue() {
        if (!platformSettings.isCronDue(PlatformSettingKey.RAG_QA_RETENTION_CRON)) {
            return;
        }
        if (!platformSettings.getBoolean(PlatformSettingKey.RAG_QA_ENABLED)) {
            return;
        }
        LocalDateTime cutoff =
                LocalDateTime.now().minusDays(platformSettings.getInt(PlatformSettingKey.RAG_QA_RETENTION_DAYS));
        int batch = 5000;
        int n = purgeLoop(() -> ragQualityAssessmentRepository.deleteOlderThan(cutoff, batch));
        if (n > 0) {
            log.info("rag quality assessment retention deleted rows={}", n);
        }
    }

    private static int purgeLoop(java.util.function.IntSupplier deleteBatch) {
        int total = 0;
        for (int i = 0; i < 200; i++) {
            int n = deleteBatch.getAsInt();
            total += n;
            if (n <= 0) {
                break;
            }
        }
        return total;
    }
}
