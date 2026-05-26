package com.aaron.cloud.common.knowledgeplanet;

import java.util.Optional;

public final class KnowledgePlanetSubjectSupport {

    private KnowledgePlanetSubjectSupport() {}

    public static Optional<Long> parseUserId(String subjectKey) {
        if (subjectKey == null || !subjectKey.startsWith("u:")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(subjectKey.substring(2).trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
