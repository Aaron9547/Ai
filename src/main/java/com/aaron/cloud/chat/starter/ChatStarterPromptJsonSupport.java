package com.aaron.cloud.chat.starter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatStarterPromptJsonSupport {

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[\\s*\"[^\\]]+]", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public String toJson(List<String> questions) {
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (Exception e) {
            throw new IllegalStateException("serialize questions failed", e);
        }
    }

    public List<String> parseQuestions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node.isArray()) {
                return extractFromArray(node);
            }
            if (node.has("questions") && node.get("questions").isArray()) {
                return extractFromArray(node.get("questions"));
            }
        } catch (Exception ignored) {
            // fall through
        }
        Matcher m = JSON_ARRAY.matcher(trimmed);
        if (m.find()) {
            try {
                return objectMapper.readValue(m.group(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // fall through
            }
        }
        return List.of();
    }

    private List<String> extractFromArray(JsonNode arr) {
        List<String> out = new ArrayList<>();
        for (JsonNode item : arr) {
            if (item.isTextual()) {
                String t = item.asText().trim();
                if (!t.isBlank() && t.length() <= 256) {
                    out.add(t);
                }
            }
        }
        return out;
    }
}
