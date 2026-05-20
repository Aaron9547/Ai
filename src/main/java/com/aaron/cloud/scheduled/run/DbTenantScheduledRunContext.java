package com.aaron.cloud.scheduled.run;

import com.aaron.cloud.common.scheduled.TenantScheduledRunRepository;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledRun;
import com.aaron.cloud.common.task.LongRunningTaskProgress;
import com.aaron.cloud.common.task.LongRunningTaskProgressSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DbTenantScheduledRunContext implements TenantScheduledRunContext {

    private final long runId;
    private final long tenantId;
    private final TenantScheduledRunRepository runRepository;
    private final ObjectMapper objectMapper;

    @Override
    public long runId() {
        return runId;
    }

    @Override
    public void report(String stage, String message, Integer percent, Integer current, Integer total) {
        runRepository
                .findById(tenantId, runId)
                .ifPresent(
                        row -> {
                            row.setProgressJson(
                                    LongRunningTaskProgressSupport.toJson(
                                            objectMapper,
                                            new LongRunningTaskProgress(
                                                    stage, message, percent, current, total)));
                            runRepository.updateById(row);
                        });
    }

    @Override
    public void recordSpawnedJobTask(long jobTaskId) {
        runRepository
                .findById(tenantId, runId)
                .ifPresent(
                        row -> {
                            List<Long> ids = parseChildIds(row.getChildJobTaskIdsJson());
                            if (!ids.contains(jobTaskId)) {
                                ids.add(jobTaskId);
                            }
                            try {
                                row.setChildJobTaskIdsJson(objectMapper.writeValueAsString(ids));
                            } catch (Exception e) {
                                log.warn("recordSpawnedJobTask serialize failed runId={}", runId, e);
                                return;
                            }
                            runRepository.updateById(row);
                        });
    }

    private List<Long> parseChildIds(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
