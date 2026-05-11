package com.aaron.cloud.common.config.providers;

public enum AuthProviderMode {
    permit,
    jwt_local,
    oauth2_resource;

    public static AuthProviderMode fromYaml(String raw) {
        if (raw == null || raw.isBlank()) {
            return permit;
        }
        String v = raw.trim().toLowerCase().replace('-', '_');
        return switch (v) {
            case "jwt_local" -> jwt_local;
            case "oauth2_resource" -> oauth2_resource;
            default -> permit;
        };
    }
}
