package com.aaron.cloud.common.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class LongRunningTaskProgressSupport {

    private LongRunningTaskProgressSupport() {}

    public static String toJson(ObjectMapper mapper, LongRunningTaskProgress p) {
        if (p == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static LongRunningTaskProgressReporter noop() {
        return (stage, message, percent, current, total, detail) -> {};
    }
}
