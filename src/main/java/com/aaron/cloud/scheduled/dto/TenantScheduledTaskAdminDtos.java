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
            String updatedAt) {}
}
