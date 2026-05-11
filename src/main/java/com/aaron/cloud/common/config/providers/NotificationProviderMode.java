package com.aaron.cloud.common.config.providers;

public enum NotificationProviderMode {
    local,
    http;

    public static NotificationProviderMode fromYaml(String raw) {
        if (raw == null || raw.isBlank()) {
            return local;
        }
        return switch (raw.trim().toLowerCase()) {
            case "http" -> http;
            default -> local;
        };
    }
}
