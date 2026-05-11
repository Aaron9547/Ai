package com.aaron.cloud.common.config.providers;

public enum FileStorageProviderMode {
    local,
    minio;

    public static FileStorageProviderMode fromYaml(String raw) {
        if (raw == null || raw.isBlank()) {
            return local;
        }
        return switch (raw.trim().toLowerCase()) {
            case "minio" -> minio;
            default -> local;
        };
    }
}
