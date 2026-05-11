package com.aaron.cloud.job.dto;

import lombok.Data;

public final class JobTaskAdminDtos {

    private JobTaskAdminDtos() {}

    @Data
    public static class JobTaskAdminView {
        private long id;
        private String taskType;
        private String status;
        private String payloadJson;
        private String resultJson;
        private String createdAt;
        private String updatedAt;
    }
}
