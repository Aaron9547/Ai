package com.aaron.cloud.rag.ltr;

import com.aaron.cloud.common.api.enums.job.JobTaskStatus;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.jobmeta.entity.JobTask;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningEffectiveService;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagLtrAdminApplicationService {

    private final RagLtrTrainingService ragLtrTrainingService;
    private final RagRetrievalTuningEffectiveService ragRetrievalTuningEffectiveService;

    public TrainResponse enqueueTrain(TrainRequest body) {
        long tenantId = TenantContextHolder.require().getTenantId();
        int days = body == null || body.days() <= 0 ? 30 : body.days();
        int maxSamples = body == null || body.maxSamples() <= 0 ? 5000 : body.maxSamples();
        try {
            long jobTaskId = ragLtrTrainingService.enqueueTrainJob(days, maxSamples);
            return new TrainResponse(jobTaskId);
        } catch (Exception e) {
            throw new IllegalStateException("入队 LTR 训练失败: " + e.getMessage(), e);
        }
    }

    public StatusResponse status() {
        long tenantId = TenantContextHolder.require().getTenantId();
        RagRetrievalTuningRuntime tuning = ragRetrievalTuningEffectiveService.effective(tenantId);
        var latest = ragLtrTrainingService.findLatestTrainJob(tenantId);
        JobTask task = latest.orElse(null);
        return new StatusResponse(
                tuning.ltrModelVersion(),
                tuning.activeLtrFileObjectId(),
                tuning.activeLtrJobTaskId(),
                task == null ? null : task.getId(),
                task == null ? null : task.getStatus(),
                task == null ? null : task.getResultJson());
    }

    public record TrainRequest(int days, int maxSamples) {}

    public record TrainResponse(long jobTaskId) {}

    public record StatusResponse(
            String ltrModelVersion,
            Long activeLtrFileObjectId,
            Long activeLtrJobTaskId,
            Long latestJobTaskId,
            JobTaskStatus latestJobStatus,
            String latestJobResultJson) {}
}
