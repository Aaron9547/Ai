package com.aaron.cloud.scheduled.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public final class TenantScheduledTaskAdminDtos {

    private TenantScheduledTaskAdminDtos() {}

    @Data
    public static class CreateScheduledTaskRequest {
        @NotBlank
        private String executorCode;

        @NotBlank
        @Size(max = 128)
        private String name;

        @NotBlank
        @Size(max = 128)
        private String cronExpression;

        private Boolean enabled;
    }

    @Data
    public static class UpdateScheduledTaskRequest {
        @Size(max = 128)
        private String name;

        @Size(max = 128)
        private String cronExpression;

        private Boolean enabled;
    }

    public record ScheduledTaskMetaView(java.util.List<java.util.Map<String, Object>> executors) {}

    public record ScheduledTaskAdminView(
            long id,
            String executorCode,
            String executorLabel,
            String name,
            String cronExpression,
            boolean enabled,
            String lastExecAt,
            String nextExecAt,
            String createdAt,
            String updatedAt,
            ScheduledRunSummaryView activeRun) {}

    public record ScheduledRunSummaryView(long runId, String status, String progressJson) {}

    public record ScheduledRunView(
            long id,
            long registrationId,
            String executorCode,
            String executorLabel,
            String status,
            String triggerType,
            String progressJson,
            String childJobTaskIdsJson,
            String errorMessage,
            String startedAt,
            String finishedAt,
            String createdAt,
            String updatedAt) {}

    public record ScheduledRunTriggerResult(
            boolean accepted,
            boolean duplicate,
            long runId,
            String status,
            ScheduledRunView run) {

        public static ScheduledRunTriggerResult started(ScheduledRunView run) {
            return new ScheduledRunTriggerResult(true, false, run.id(), run.status(), run);
        }

        public static ScheduledRunTriggerResult existing(ScheduledRunView run) {
            return new ScheduledRunTriggerResult(false, true, run.id(), run.status(), run);
        }
    }
}
