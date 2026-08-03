package com.aaron.cloud.rag.ltr;

import com.aaron.cloud.common.api.enums.job.JobTaskStatus;
import com.aaron.cloud.common.api.enums.job.JobTaskType;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.jobmeta.JobTaskAsyncDispatcher;
import com.aaron.cloud.common.jobmeta.JobTaskRepository;
import com.aaron.cloud.common.jobmeta.entity.JobTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** RAG LTR 训练任务入队与状态查询（执行见 {@link RagLtrTrainJobRunner}）。 */
@Service
@RequiredArgsConstructor
public class RagLtrTrainingService {

    private final JobTaskRepository jobTaskRepository;
    private final ObjectMapper objectMapper;
    private final JobTaskAsyncDispatcher jobTaskAsyncDispatcher;

    public Optional<JobTask> findLatestTrainJob(long tenantId) {
        var page = jobTaskRepository.pageByTenant(tenantId, 1, 1, JobTaskType.RAG_LTR_TRAIN, null);
        if (page.getRecords().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(page.getRecords().getFirst());
    }

    public long enqueueTrainJob(int days, int maxSamples) throws Exception {
        var snap = TenantContextHolder.require();
        Map<String, Object> payload = new HashMap<>();
        payload.put("days", days <= 0 ? RagLtrTrainJobRunner.DEFAULT_DAYS : days);
        payload.put("maxSamples", maxSamples <= 0 ? RagLtrTrainJobRunner.DEFAULT_MAX_SAMPLES : maxSamples);
        var task = new JobTask();
        task.setTenantId(snap.getTenantId());
        task.setUserId(snap.getUserId());
        task.setDeviceId(snap.getDeviceId());
        task.setTaskType(JobTaskType.RAG_LTR_TRAIN);
        task.setStatus(JobTaskStatus.PENDING);
        task.setPayloadJson(objectMapper.writeValueAsString(payload));
        jobTaskRepository.insert(task);
        jobTaskAsyncDispatcher.dispatch(task);
        return task.getId();
    }
}
