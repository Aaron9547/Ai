package com.aaron.cloud.common.config.providers;

public enum VectorStoreProviderMode {
    local,
    milvus;

    public static VectorStoreProviderMode fromYaml(String raw) {
        if (raw == null || raw.isBlank()) {
            return local;
        }
        return switch (raw.trim().toLowerCase()) {
            case "milvus" -> milvus;
            default -> local;
        };
    }
}
